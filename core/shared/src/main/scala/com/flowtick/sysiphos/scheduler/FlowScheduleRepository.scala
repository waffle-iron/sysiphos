package com.flowtick.sysiphos.scheduler

import com.flowtick.sysiphos.core.RepositoryContext

import scala.concurrent.Future

trait FlowScheduleRepository[+T <: FlowSchedule] {
  def addFlowSchedule(
    id: String,
    expression: String,
    flowDefinitionId: String,
    flowTaskId: Option[String],
    nextDueDate: Option[Long],
    enabled: Option[Boolean])(implicit repositoryContext: RepositoryContext): Future[T]
  def getFlowSchedules()(implicit repositoryContext: RepositoryContext): Future[Seq[T]]
}

trait FlowScheduleStateStore {
  def setDueDate(flowScheduleId: String, dueDate: Long)(implicit repositoryContext: RepositoryContext): Future[Unit]
}
