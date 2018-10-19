package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }

import scala.concurrent.{ ExecutionContext, Future }

trait FlowExecution extends Logging {
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
    flowTaskInstanceRepository.getFlowTaskInstances(None, Some(now), Some(Seq(FlowTaskInstanceStatus.Retry))).flatMap { tasks =>
      Future.sequence(tasks.map { task =>
        flowInstanceRepository.findById(task.flowInstanceId).map(instance => (instance, task.taskId))
      })
    }

  def manuallyTriggeredInstances: Future[Seq[FlowInstance]] =
    flowInstanceRepository.getFlowInstances(FlowInstanceQuery(
      flowDefinitionId = None,
      instanceIds = None,
      status = Some(Seq(FlowInstanceStatus.Triggered)),
      createdGreaterThan = None))

  def dueScheduledFlowInstances(now: Long): Future[Seq[FlowInstance]] = {
    log.debug("tick.")
    val futureEnabledSchedules: Future[Seq[FlowSchedule]] = flowScheduleRepository
      .getFlowSchedules(enabled = Some(true), None)

    for {
      schedules: Seq[FlowSchedule] <- futureEnabledSchedules
      applied <- Future.sequence(schedules.map(applySchedule(_, now)))
    } yield applied.flatten
  }

  def applySchedule(schedule: FlowSchedule, now: Long): Future[Seq[FlowInstance]] = {
    val potentialInstance: Future[Seq[FlowInstance]] = if (isDue(schedule, now))
      createFlowInstance(schedule).map(Seq(_))
    else Future.successful(Seq.empty)

    val missedInstances: Future[Seq[FlowInstance]] = if (schedule.backFill.contains(true))
      Future.sequence(flowScheduler.missedOccurrences(schedule, now).map { _ => createFlowInstance(schedule) })
    else Future.successful(Seq.empty)

    val potentialInstances = for {
      instance <- potentialInstance
      missed <- missedInstances
    } yield instance ++ missed

    potentialInstances.recoverWith {
      case error =>
        log.error("unable to create instance", error)
        Future.successful(Seq.empty)
    }.flatMap { instances =>
      flowScheduler.nextOccurrence(schedule, now).map { next =>
        setNextDueDate(schedule, next)
      }.getOrElse(Future.successful()).map { _ => instances }
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

  def executeInstance(instance: FlowInstance, selectedTaskId: Option[String]): Future[FlowInstanceExecution.FlowInstanceMessage]

}
