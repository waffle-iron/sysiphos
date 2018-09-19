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
    flowInstanceRepository.createFlowInstance(flowSchedule.flowDefinitionId, Map.empty, FlowInstanceStatus.Scheduled)
  }

  def dueTaskRetries(now: Long): Future[Seq[FlowInstance]] = {
    flowTaskInstanceRepository.getScheduled().flatMap { tasks =>
      Future.sequence(tasks.map { task =>
        task.nextDueDate match {
          case Some(timestamp) if timestamp <= now =>
            log.info(s"found ${task.id} that is due for retry")
            for {
              instance <- flowInstanceRepository.findById(task.flowInstanceId)
              _ <- flowTaskInstanceRepository.setNextDueDate(task.id, None)
              _ <- flowTaskInstanceRepository.setRetries(task.id, task.retries - 1)
            } yield instance
          case None => Future.successful(None)
        }
      })
    }.map(_.flatten)
  }

  def manuallyTriggeredInstances: Future[Seq[FlowInstance]] =
    flowInstanceRepository.getFlowInstances(FlowInstanceQuery(
      flowDefinitionId = None,
      instanceIds = None,
      status = Some(FlowInstanceStatus.ManuallyTriggered),
      createdGreaterThan = None))

  def dueScheduledFlowInstances(now: Long): Future[Seq[FlowInstance]] = {
    log.debug("tick.")
    val futureEnabledSchedules: Future[Seq[FlowSchedule]] = flowScheduleRepository
      .getFlowSchedules(enabled = Some(true), None)

    for {
      schedules: Seq[FlowSchedule] <- futureEnabledSchedules
      applied: Seq[Option[FlowInstance]] <- Future.sequence(schedules.map(applySchedule(_, now)))
    } yield applied.flatten
  }

  def applySchedule(schedule: FlowSchedule, now: Long): Future[Option[FlowInstance]] = {
    val potentialInstance = if (isDue(schedule, now)) {
      createFlowInstance(schedule).map(Option(_))
    } else Future.successful(None)

    potentialInstance.recoverWith {
      case error =>
        log.error("unable to create instance", error)
        Future.successful(None)
    }.flatMap {
      case Some(newInstance) =>
        val next: Option[Long] = flowScheduler.nextOccurrence(schedule, now)
        next
          .map(setNextDueDate(schedule, _))
          .getOrElse(Future.successful(()))
          .map(_ => Some(newInstance))
      case None => Future.successful(None)
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

}
