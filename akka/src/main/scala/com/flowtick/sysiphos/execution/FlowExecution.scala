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
      .getFlowSchedules(onlyEnabled = true, None)

    futureEnabledSchedules.flatMap { schedules =>
      log.debug(s"checking schedules: $schedules.")
      Future.sequence {
        schedules.map { s =>
          val maybeFlowInstance = createFlowInstanceIfDue(s, now).recoverWith {
            case e =>
              log.error("unable to create instance", e)
              Future.successful(None)
          }

          // schedule next occurrence
          maybeFlowInstance.foreach { _ =>
            flowScheduler
              .nextOccurrence(s, now)
              .map(next => flowScheduleStateStore.setDueDate(s.id, next))
          }

          maybeFlowInstance
        }
      }
    }.map(_.flatten)
  }

  def createFlowInstanceIfDue(schedule: FlowSchedule, now: Long): Future[Option[FlowInstance]] = {
    log.debug(s"checking if $schedule is due.")
    schedule.nextDueDate match {
      case Some(timestamp) if timestamp <= now =>
        createFlowInstance(schedule).map(Option(_))
      case None if schedule.enabled.contains(true) && schedule.expression.isDefined =>
        createFlowInstance(schedule).map(Option(_))
      case _ =>
        log.debug(s"not due: $schedule")
        Future.successful(None)
    }
  }

}
