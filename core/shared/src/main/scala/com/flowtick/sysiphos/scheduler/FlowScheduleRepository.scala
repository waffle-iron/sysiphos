package com.flowtick.sysiphos.scheduler

import scala.concurrent.Future

trait FlowScheduleRepository {
  def getFlowSchedules: Future[Seq[FlowSchedule]]
  def setDueDate(flowSchedule: FlowSchedule, due: Long): Future[Unit]
}

class InMemoryFlowScheduleRepository(schedules: Seq[FlowSchedule]) extends FlowScheduleRepository {
  override def getFlowSchedules: Future[Seq[FlowSchedule]] = Future.successful(schedules)

  override def setDueDate(flowSchedule: FlowSchedule, due: Long) = throw new UnsupportedOperationException
}
