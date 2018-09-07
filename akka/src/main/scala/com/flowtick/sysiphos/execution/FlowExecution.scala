package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowInstance, FlowInstanceRepository }
import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }

import scala.concurrent.{ ExecutionContext, Future }

trait FlowExecution extends Logging {
  val flowScheduleRepository: FlowScheduleRepository
  val flowInstanceRepository: FlowInstanceRepository
  val flowScheduleStateStore: FlowScheduleStateStore
  val flowScheduler: FlowScheduler
  implicit val repositoryContext: RepositoryContext
  implicit val executionContext: ExecutionContext

  def createInstance(flowSchedule: FlowSchedule): Future[FlowInstance] = {
    log.debug(s"creating instance for $flowSchedule.")
    flowInstanceRepository.createFlowInstance(flowSchedule.flowDefinitionId, Map.empty)
  }

  def dueTaskInstances(now: Long): Future[Seq[FlowInstance]] = {
    log.debug("tick.")
    val futureEnabledSchedules: Future[Seq[FlowSchedule]] = flowScheduleRepository
      .getFlowSchedules(onlyEnabled = true, None)

    futureEnabledSchedules.flatMap { schedules =>
      log.debug(s"checking schedules: $schedules.")
      Future.sequence {
        schedules.map { s =>
          val maybeFlowInstance = createInstanceIfDue(s, now)

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

  def createInstanceIfDue(schedule: FlowSchedule, now: Long): Future[Option[FlowInstance]] = {
    log.debug(s"checking if $schedule is due.")
    schedule.nextDueDate match {
      case Some(timestamp) if timestamp <= now =>
        createInstance(schedule).map(Option(_))
      case None if schedule.enabled.contains(true) && schedule.expression.isDefined =>
        createInstance(schedule).map(Option(_))
      case _ =>
        log.debug(s"not due: $schedule")
        Future.successful(None)
    }
  }

}
