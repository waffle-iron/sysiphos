package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.core.RepositoryContext

import scala.concurrent.Future

final case class FlowTaskInstanceQuery(
  id: Option[String] = None,
  taskId: Option[String] = None,
  flowInstanceId: Option[String] = None,
  dueBefore: Option[Long] = None,
  status: Option[Seq[FlowTaskInstanceStatus.FlowTaskInstanceStatus]] = None)

trait FlowTaskInstanceRepository {
  def find(query: FlowTaskInstanceQuery)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowTaskInstanceDetails]]
  def findOne(query: FlowTaskInstanceQuery)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]]

  def createFlowTaskInstance(
    flowInstanceId: String,
    flowTaskId: String,
    flowDefinitionId: String,
    logId: String,
    retries: Int,
    retryDelay: Long,
    dueDate: Option[Long],
    initialStatus: Option[FlowTaskInstanceStatus.FlowTaskInstanceStatus])(implicit repositoryContext: RepositoryContext): Future[FlowTaskInstanceDetails]

  def setStatus(flowTaskInstanceId: String, status: FlowTaskInstanceStatus.FlowTaskInstanceStatus, retries: Option[Int], nextRetry: Option[Long])(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]]
  def setStartTime(taskInstanceId: String, startTime: Long)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]]
  def setEndTime(taskInstanceId: String, endTime: Long)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]]
  def deleteFlowTaskInstance(flowTaskInstanceId: String)(implicit repositoryContext: RepositoryContext): Future[String]
}
