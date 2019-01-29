package com.flowtick.sysiphos.execution

import cats.data.{ EitherT, OptionT }
import cats.instances.future._
import com.flowtick.sysiphos.core.{ Clock, RepositoryContext }
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }

import scala.concurrent.{ ExecutionContext, Future }
import Logging._

trait FlowExecution extends Logging with Clock {
  val flowDefinitionRepository: FlowDefinitionRepository
  val flowScheduleRepository: FlowScheduleRepository
  val flowInstanceRepository: FlowInstanceRepository
  val flowScheduleStateStore: FlowScheduleStateStore
  val flowScheduler: FlowScheduler
  val flowTaskInstanceRepository: FlowTaskInstanceRepository

  implicit val repositoryContext: RepositoryContext
  implicit val executionContext: ExecutionContext

  def createFlowInstance(flowSchedule: FlowSchedule): Future[FlowInstance] = {
    log.debug(s"creating instance for $flowSchedule.")
    flowInstanceRepository.createFlowInstance(flowSchedule.flowDefinitionId, Seq.empty, FlowInstanceStatus.Scheduled)
  }

  def dueTaskRetries(now: Long): Future[Seq[(Option[FlowInstance], String)]] =
    flowTaskInstanceRepository.find(FlowTaskInstanceQuery(dueBefore = Some(now), status = Some(Seq(FlowTaskInstanceStatus.Retry)))).flatMap { tasks =>
      Future.sequence(tasks.map { task =>
        flowInstanceRepository.findById(task.flowInstanceId).map(instance => (instance, task.taskId))
      })
    }.logFailed(s"unable to check for retries")

  def manuallyTriggeredInstances: Future[Option[Seq[FlowInstance]]] =
    flowInstanceRepository.getFlowInstances(FlowInstanceQuery(
      flowDefinitionId = None,
      instanceIds = None,
      status = Some(Seq(FlowInstanceStatus.Triggered)),
      createdGreaterThan = None)).map(Option.apply)

  def dueScheduledFlowInstances(now: Long): Future[Option[Seq[FlowInstance]]] = {
    log.debug("tick.")
    val futureEnabledSchedules: Future[Seq[FlowSchedule]] = flowScheduleRepository
      .getFlowSchedules(enabled = Some(true), None)

    for {
      schedules: Seq[FlowSchedule] <- futureEnabledSchedules
      applied: Seq[Option[Seq[FlowInstance]]] <- Future.sequence(schedules.map(applySchedule(_, now)))
    } yield Some(applied.flatten.flatten)
  }

  def applySchedule(schedule: FlowSchedule, now: Long): Future[Option[Seq[FlowInstance]]] = {
    val dueInstances: Future[Option[Seq[FlowInstance]]] = if (isDue(schedule, now)) {
      Future
        .successful(flowScheduler.nextOccurrence(schedule, now))
        .flatMap {
          case Some(nextDue) => setNextDueDate(schedule, nextDue).map(_ => Some(nextDue))
          case None => Future.successful(None)
        }
        .flatMap {
          case Some(_) => createFlowInstance(schedule).map(Seq(_)).map(Option.apply)
          case None => Future.successful(None)
        }
    } else Future.successful(None)

    val missedInstances: Future[Option[Seq[FlowInstance]]] = if (isDue(schedule, now) && schedule.backFill.contains(true)) {
      Future
        .sequence(flowScheduler.missedOccurrences(schedule, now).map { _ => createFlowInstance(schedule) })
        .map(Option.apply)
    } else Future.successful(None)

    val potentialInstances: Future[Option[Seq[FlowInstance]]] = for {
      newInstances <- dueInstances
      missed <- missedInstances
    } yield Some(newInstances.getOrElse(Seq.empty) ++ missed.getOrElse(Seq.empty))

    potentialInstances.recoverWith {
      case error =>
        log.error("unable to create instance", error)
        Future.successful(None)
    }
  }

  def setNextDueDate(schedule: FlowSchedule, next: Long): Future[Unit] =
    flowScheduleStateStore.setDueDate(schedule.id, next).recoverWith {
      case error =>
        log.error("unable to set due date", error)
        Future.successful(())
    }

  def isDue(schedule: FlowSchedule, now: Long): Boolean =
    schedule.nextDueDate match {
      case Some(timestamp) if timestamp <= now => true
      case None if schedule.enabled.contains(true) && schedule.expression.isDefined => true
      case _ => false
    }

  def latestOnly(definition: FlowDefinition, instancesToRun: Seq[FlowInstance]): Future[Seq[FlowInstance]] =
    if (definition.latestOnly) {
      val lastInstances: Seq[FlowInstance] = instancesToRun
        .groupBy(_.context.toSet)
        .flatMap { case (_, instances) => if (instances.isEmpty) None else Some(instances.maxBy(_.creationTime)) }
        .toSeq

      val olderInstances = instancesToRun.filterNot(lastInstances.contains)

      val skipOlderInstances: Future[Seq[FlowInstanceDetails]] = Future.sequence(
        olderInstances.map { instance =>
          flowInstanceRepository.setStatus(instance.id, FlowInstanceStatus.Skipped)
        }).map(_.flatten)

      skipOlderInstances.map(_ => lastInstances)
    } else Future.successful(instancesToRun)

  def executeInstances(
    flowDefinition: FlowDefinition,
    instancesToRun: Seq[FlowInstance]): Future[Seq[FlowInstance]] = {
    def runningInstances(flowDefinitionId: String): Future[Seq[InstanceCount]] =
      flowInstanceRepository.counts(Option(Seq(flowDefinitionId)), Option(Seq(FlowInstanceStatus.Running)), None)

    val withParallelism = runningInstances(flowDefinition.id).map { counts =>
      val runningInstances = counts
        .groupBy(_.flowDefinitionId)
        .getOrElse(flowDefinition.id, Seq.empty)
        .find(_.status == FlowInstanceStatus.Running.toString)
        .map(_.count)
        .getOrElse(0)

      val chosenInstances = for {
        parallelism <- Option(flowDefinition.parallelism.getOrElse(Integer.MAX_VALUE))
        newInstances <- Option(parallelism - runningInstances).filter(_ > 0)
      } yield instancesToRun.take(newInstances)

      chosenInstances.getOrElse(Seq.empty)
    }

    withParallelism.flatMap(instances => Future.sequence(instances.map(executeInstance(_, None))))
  }

  def executeInstance(instance: FlowInstance, selectedTaskId: Option[String]): Future[FlowInstance] = {
    val runningInstance = for {
      _ <- if (instance.startTime.isEmpty)
        flowInstanceRepository.setStartTime(instance.id, repositoryContext.epochSeconds)
      else
        Future.successful(())
      running <- flowInstanceRepository.setStatus(instance.id, FlowInstanceStatus.Running)
    } yield running

    EitherT
      .fromOptionF(runningInstance, "unable to update flow instance")
      .semiflatMap(instance => executeRunning(instance, selectedTaskId).map(_ => instance))
      .value.flatMap {
        case Left(message) => Future.failed(new IllegalStateException(message))
        case Right(value) => Future.successful(value)
      }
  }

  def executeRunning(
    running: FlowInstanceDetails,
    selectedTaskId: Option[String]): Future[Any]

  def executeScheduled(): Unit = {
    val taskInstancesFuture: Future[Option[Seq[FlowInstance]]] = for {
      manuallyTriggered <- manuallyTriggeredInstances.logFailed("unable to get manually triggered instances")
      newTaskInstances <- dueScheduledFlowInstances(epochSeconds).logFailed("unable to get scheduled flow instance")
    } yield Some(newTaskInstances.getOrElse(Seq.empty) ++ manuallyTriggered.getOrElse(Seq.empty))

    for {
      instances: Option[Seq[FlowInstance]] <- taskInstancesFuture
      newInstances <- Future.successful(instances.getOrElse(Seq.empty).groupBy(_.flowDefinitionId).toSeq.map {
        case (flowDefinitionId, triggeredInstances) =>
          val flowDefinitionFuture: Future[Option[FlowDefinitionDetails]] = flowDefinitionRepository.findById(flowDefinitionId)

          (for {
            flowDefinitionOrError: Either[Exception, FlowDefinition] <- OptionT(flowDefinitionFuture)
              .getOrElseF(Future.failed(new IllegalStateException("flow definition not found")))
              .map(FlowDefinition.apply)
            flowDefinition <- flowDefinitionOrError match {
              case Right(flowDefinition) => Future.successful(flowDefinition)
              case Left(error) => Future.failed(new IllegalStateException("unable to parse flow definition", error))
            }
            latestTriggered <- latestOnly(flowDefinition, triggeredInstances)
            running <- executeInstances(flowDefinition, latestTriggered)
          } yield running): Future[Seq[FlowInstance]]
      })
    } yield newInstances
  }

  def executeRetries(): Unit = {
    dueTaskRetries(epochSeconds).logFailed("unable to get due tasks").foreach { dueTasks =>
      dueTasks.foreach {
        case (Some(instance), taskId) => executeInstance(instance, Some(taskId))
        case _ =>
      }
    }
  }
}
