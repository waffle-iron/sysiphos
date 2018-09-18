package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.core.RepositoryContext

import scala.concurrent.Future

trait FlowTaskInstanceRepository {
  def findById(id: String)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]]
  def getFlowTaskInstances(flowInstanceId: String)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowTaskInstanceDetails]]
  def createFlowTaskInstance(flowInstanceId: String, flowTaskId: String)(implicit repositoryContext: RepositoryContext): Future[FlowTaskInstanceDetails]
  def setStatus(flowTaskInstanceId: String, status: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]]
  def setStartTime(taskInstanceId: String, startTime: Long)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]]
  def setEndTime(taskInstanceId: String, endTime: Long)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]]
  def setRetries(flowTaskInstanceId: String, retries: Int)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]]
  def setLogId(flowTaskInstanceId: String, logId: String)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]]
  def setNextDueDate(flowTaskInstanceId: String, nextDueDate: Option[Long])(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]]
  def getScheduled()(implicit repositoryContext: RepositoryContext): Future[Seq[FlowTaskInstanceDetails]]
}
