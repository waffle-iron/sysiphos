package com.flowtick.sysiphos.scheduler

import com.flowtick.sysiphos.core.RepositoryContext

import scala.concurrent.Future

trait FlowScheduleRepository {
  def createFlowSchedule(
    id: Option[String],
    expression: Option[String],
    flowDefinitionId: String,
    flowTaskId: Option[String],
    enabled: Option[Boolean],
    backFill: Option[Boolean])(implicit repositoryContext: RepositoryContext): Future[FlowScheduleDetails]

  def updateFlowSchedule(
    id: String,
    expression: Option[String],
    enabled: Option[Boolean],
    backFill: Option[Boolean])(implicit repositoryContext: RepositoryContext): Future[FlowScheduleDetails]

  def getFlowSchedules(
    enabled: Option[Boolean],
    flowId: Option[String])(implicit repositoryContext: RepositoryContext): Future[Seq[FlowScheduleDetails]]

  def findById(id: String)(implicit repositoryContext: RepositoryContext): Future[Option[FlowScheduleDetails]]
}

trait FlowScheduleStateStore {
  def setDueDate(flowScheduleId: String, dueDate: Long)(implicit repositoryContext: RepositoryContext): Future[Unit]
}
