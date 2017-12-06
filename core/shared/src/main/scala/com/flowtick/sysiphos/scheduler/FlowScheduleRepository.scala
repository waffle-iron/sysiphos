package com.flowtick.sysiphos.scheduler

import scala.concurrent.Future

trait FlowScheduleRepository {
  def addFlowSchedule(flowSchedule: FlowSchedule): Future[FlowSchedule]
  def getFlowSchedules: Future[Seq[FlowSchedule]]
  def setDueDate(flowSchedule: FlowSchedule, due: Long): Future[Unit]
}
