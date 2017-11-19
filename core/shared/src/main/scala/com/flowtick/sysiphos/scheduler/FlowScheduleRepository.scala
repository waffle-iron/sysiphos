package com.flowtick.sysiphos.scheduler

import scala.concurrent.Future

trait FlowScheduleRepository {
  def getFlowSchedules(onlyDue: Boolean = false): Future[Seq[FlowSchedule]]
  def setDueDate(flowSchedule: FlowSchedule, due: Long): Future[Unit]
}

class InMemoryFlowScheduleRepository(schedules: Seq[FlowSchedule]) extends FlowScheduleRepository {
  override def getFlowSchedules(onlyDue: Boolean) = Future.successful(schedules)

  override def setDueDate(flowSchedule: FlowSchedule, due: Long) = throw new UnsupportedOperationException
}
