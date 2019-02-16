package com.flowtick.sysiphos.execution

import cats.data.{ EitherT, OptionT }
import cats.instances.future._
import cats.instances.list._
import cats.syntax.traverse._
import com.flowtick.sysiphos.core.{ Clock, RepositoryContext }
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }

import scala.concurrent.{ ExecutionContext, Future }
import Logging._

import cats.effect.IO
import com.flowtick.sysiphos.config.Configuration

trait FlowExecution extends Logging with Clock {
  val flowDefinitionRepository: FlowDefinitionRepository
  val flowScheduleRepository: FlowScheduleRepository
  val flowInstanceRepository: FlowInstanceRepository
  val flowScheduleStateStore: FlowScheduleStateStore
  val flowScheduler: FlowScheduler
  val flowTaskInstanceRepository: FlowTaskInstanceRepository

  def retryBatchSize: Int = Configuration.propOrEnv("retry.batch.size").map(_.toInt).getOrElse(20)

  implicit val repositoryContext: RepositoryContext
  implicit val executionContext: ExecutionContext

  def createFlowInstance(flowSchedule: FlowSchedule): Future[FlowInstanceContext] = {
    log.debug(s"creating instance for $flowSchedule.")
    flowInstanceRepository.createFlowInstance(flowSchedule.flowDefinitionId, Seq.empty, FlowInstanceStatus.Scheduled)
  }

  def dueTaskRetries(now: Long): Future[Seq[(Option[FlowInstanceContext], String)]] = {
    val taskQuery = FlowTaskInstanceQuery(
      dueBefore = Some(now),
      status = Some(Seq(FlowTaskInstanceStatus.Retry)),
      limit = Some(retryBatchSize))

    def findInstance(task: FlowTaskInstanceDetails) = {
      val query = FlowInstanceQuery(
        flowDefinitionId = Some(task.flowDefinitionId),
        instanceIds = Some(Seq(task.flowInstanceId)),
        status = Some(Seq(FlowInstanceStatus.Running)))

      flowInstanceRepository
        .findContext(query)
        .map(_.map(context => (Some(context), task.id)))
    }

    flowTaskInstanceRepository.find(taskQuery).flatMap { tasks =>
      Future.sequence(tasks.map(findInstance)).map(_.flatten)
    }.logFailed(s"unable to check for retries")
  }

  def manuallyTriggeredInstances: Future[Option[Seq[FlowInstanceContext]]] =
    flowInstanceRepository.findContext(FlowInstanceQuery(
      flowDefinitionId = None,
      instanceIds = None,
      status = Some(Seq(FlowInstanceStatus.Triggered)),
      createdGreaterThan = None)).map(Option.apply)

  def dueScheduledFlowInstances(now: Long): Future[Option[Seq[FlowInstanceContext]]] = {
    log.debug("tick.")
    val futureEnabledSchedules: Future[Seq[FlowSchedule]] = flowScheduleRepository
      .getFlowSchedules(enabled = Some(true), None)

    for {
      schedules: Seq[FlowSchedule] <- futureEnabledSchedules
      applied: Seq[Option[Seq[FlowInstanceContext]]] <- Future.sequence(schedules.map(applySchedule(_, now)))
    } yield Some(applied.flatten.flatten)
  }

  def applySchedule(schedule: FlowSchedule, now: Long): Future[Option[Seq[FlowInstanceContext]]] = {
    val dueInstances: IO[Option[Seq[FlowInstanceContext]]] = if (isDue(schedule, now)) {
      IO
        .pure(flowScheduler.nextOccurrence(schedule, now))
        .flatMap {
          case Some(nextDue) => setNextDueDate(schedule, nextDue).map(_ => Some(nextDue))
          case None => IO.pure(None)
        }
        .flatMap {
          case Some(_) => IO.fromFuture(IO(createFlowInstance(schedule).map(Seq(_)).map(Option.apply)))
          case None => IO.pure(None)
        }
    } else IO.pure(None)

    val missedInstances: IO[Option[Seq[FlowInstanceContext]]] = if (isDue(schedule, now) && schedule.backFill.getOrElse(false)) {
      flowScheduler
        .missedOccurrences(schedule, now)
        .map { _ => IO.fromFuture(IO(createFlowInstance(schedule))) }
        .toList
        .sequence
        .map(Option.apply)
    } else IO.pure(None)

    val potentialInstances: IO[Option[Seq[FlowInstanceContext]]] = for {
      newInstances <- dueInstances
      missed <- missedInstances
    } yield Some(newInstances.getOrElse(Seq.empty) ++ missed.getOrElse(Seq.empty))

    potentialInstances.handleErrorWith {
      error =>
        log.error("unable to create instance", error)
        IO.pure(None)
    }.unsafeToFuture()
  }

  def setNextDueDate(schedule: FlowSchedule, next: Long): IO[Unit] =
    IO.fromFuture(IO(flowScheduleStateStore.setDueDate(schedule.id, next)))

  def isDue(schedule: FlowSchedule, now: Long): Boolean =
    schedule.nextDueDate match {
      case Some(timestamp) if timestamp <= now => true
      case None if schedule.enabled.contains(true) && schedule.expression.isDefined => true
      case _ => false
    }

  def latestOnly(definition: FlowDefinition, instancesToRun: Seq[FlowInstanceContext]): Future[Seq[FlowInstanceContext]] =
    if (definition.latestOnly) {
      val lastInstances: Seq[FlowInstanceContext] = instancesToRun
        .groupBy(_.context.toSet)
        .flatMap { case (_, instances) => if (instances.isEmpty) None else Some(instances.maxBy(_.instance.creationTime)) }
        .toSeq

      val olderInstances = instancesToRun.filterNot(lastInstances.contains)

      val skipOlderInstances: Future[Seq[FlowInstanceDetails]] = Future.sequence(
        olderInstances.map { context =>
          flowInstanceRepository.setStatus(context.instance.id, FlowInstanceStatus.Skipped)
        }).map(_.flatten)

      skipOlderInstances.map(_ => lastInstances)
    } else Future.successful(instancesToRun)

  def executeInstances(
    flowDefinition: FlowDefinition,
    instancesToRun: Seq[FlowInstanceContext]): Future[Seq[FlowInstance]] = {
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

  def executeInstance(context: FlowInstanceContext, selectedTaskId: Option[String]): Future[FlowInstance] = {
    val runningInstance = for {
      _ <- if (context.instance.startTime.isEmpty)
        flowInstanceRepository.setStartTime(context.instance.id, repositoryContext.epochSeconds)
      else
        Future.successful(())
      running <- flowInstanceRepository.setStatus(context.instance.id, FlowInstanceStatus.Running)
    } yield running

    EitherT
      .fromOptionF(runningInstance, ifNone = "unable to update flow instance")
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
    val taskInstancesFuture: Future[Option[Seq[FlowInstanceContext]]] = for {
      manuallyTriggered <- manuallyTriggeredInstances.logFailed("unable to get manually triggered instances")
      newTaskInstances <- dueScheduledFlowInstances(currentTime.toEpochSecond).logFailed("unable to get scheduled flow instance")
    } yield Some(newTaskInstances.getOrElse(Seq.empty) ++ manuallyTriggered.getOrElse(Seq.empty))

    for {
      instances: Option[Seq[FlowInstanceContext]] <- taskInstancesFuture
      newInstances <- Future.successful(instances.getOrElse(Seq.empty).groupBy(_.instance.flowDefinitionId).toSeq.map {
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
    dueTaskRetries(currentTime.toEpochSecond).logFailed("unable to get due tasks").foreach { dueTasks =>
      dueTasks.foreach {
        case (Some(instance), taskId) => executeInstance(instance, Some(taskId))
        case _ =>
      }
    }
  }
}
