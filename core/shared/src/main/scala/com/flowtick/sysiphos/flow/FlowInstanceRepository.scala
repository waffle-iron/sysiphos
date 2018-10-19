package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.FlowInstanceStatus.FlowInstanceStatus

import scala.concurrent.Future

final case class FlowInstanceQuery(
  flowDefinitionId: Option[String],
  instanceIds: Option[Seq[String]] = None,
  status: Option[Seq[FlowInstanceStatus]] = None,
  createdGreaterThan: Option[Long] = None)
final case class InstanceCount(flowDefinitionId: String, status: String, count: Int)
final case class FlowDefinitionSummary(id: String, counts: Seq[InstanceCount])

trait FlowInstanceRepository {
  def findById(id: String)(implicit repositoryContext: RepositoryContext): Future[Option[FlowInstanceDetails]]
  def setStatus(id: String, status: FlowInstanceStatus.FlowInstanceStatus)(implicit repositoryContext: RepositoryContext): Future[Option[FlowInstanceDetails]]
  def setStartTime(flowInstanceId: String, startTime: Long)(implicit repositoryContext: RepositoryContext): Future[Option[FlowInstanceDetails]]
  def setEndTime(flowInstanceId: String, endTime: Long)(implicit repositoryContext: RepositoryContext): Future[Option[FlowInstanceDetails]]
  def getFlowInstances(query: FlowInstanceQuery)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowInstanceDetails]]
  def createFlowInstance(flowDefinitionId: String, context: Seq[FlowInstanceContextValue], initialStatus: FlowInstanceStatus)(implicit repositoryContext: RepositoryContext): Future[FlowInstanceDetails]
  def counts(flowDefinitionId: Option[Seq[String]], status: Option[Seq[FlowInstanceStatus.FlowInstanceStatus]], createdGreaterThan: Option[Long]): Future[Seq[InstanceCount]]
}

