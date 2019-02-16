package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.FlowInstanceStatus.FlowInstanceStatus

import scala.concurrent.Future

final case class FlowInstanceQuery(
  flowDefinitionId: Option[String],
  instanceIds: Option[Seq[String]] = None,
  status: Option[Seq[FlowInstanceStatus]] = None,
  createdGreaterThan: Option[Long] = None,
  createdSmallerThan: Option[Long] = None,
  offset: Option[Int] = None,
  limit: Option[Int] = None)

final case class InstanceCount(flowDefinitionId: String, status: String, count: Int)
final case class FlowDefinitionSummary(id: String, counts: Seq[InstanceCount])

trait FlowInstanceRepository {
  def findById(id: String)(implicit repositoryContext: RepositoryContext): Future[Option[FlowInstanceDetails]]
  def findContext(query: FlowInstanceQuery)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowInstanceContext]]
  def getContextValues(id: String)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowInstanceContextValue]]
  def setStatus(id: String, status: FlowInstanceStatus.FlowInstanceStatus)(implicit repositoryContext: RepositoryContext): Future[Option[FlowInstanceDetails]]
  def update(query: FlowInstanceQuery, status: FlowInstanceStatus.FlowInstanceStatus)(implicit repositoryContext: RepositoryContext): Future[Unit]
  def setStartTime(flowInstanceId: String, startTime: Long)(implicit repositoryContext: RepositoryContext): Future[Option[FlowInstanceDetails]]
  def setEndTime(flowInstanceId: String, endTime: Long)(implicit repositoryContext: RepositoryContext): Future[Option[FlowInstanceDetails]]
  def getFlowInstances(query: FlowInstanceQuery)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowInstanceDetails]]
  def createFlowInstance(flowDefinitionId: String, context: Seq[FlowInstanceContextValue], initialStatus: FlowInstanceStatus)(implicit repositoryContext: RepositoryContext): Future[FlowInstanceContext]
  def insertOrUpdateContextValues(flowInstanceId: String, contextValues: Seq[FlowInstanceContextValue])(implicit repositoryContext: RepositoryContext): Future[Option[FlowInstanceDetails]]
  def counts(flowDefinitionId: Option[Seq[String]], status: Option[Seq[FlowInstanceStatus.FlowInstanceStatus]], createdGreaterThan: Option[Long]): Future[Seq[InstanceCount]]
  def deleteFlowInstance(flowInstanceId: String)(implicit repositoryContext: RepositoryContext): Future[String]
}

