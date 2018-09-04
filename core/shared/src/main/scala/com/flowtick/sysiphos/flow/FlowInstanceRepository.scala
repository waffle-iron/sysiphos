package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.core.RepositoryContext

import scala.concurrent.Future

final case class FlowInstanceQuery(flowDefinitionId: Option[String])
final case class InstanceCount(flowDefinitionId: String, status: String, count: Int)
final case class FlowDefinitionSummary(id: String, counts: Seq[InstanceCount])

trait FlowInstanceRepository {
  def setStatus(id: String, status: String)(implicit repositoryContext: RepositoryContext): Future[Unit]
  def getFlowInstances(query: FlowInstanceQuery)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowInstanceDetails]]
  def createFlowInstance(flowDefinitionId: String, context: Map[String, String])(implicit repositoryContext: RepositoryContext): Future[FlowInstanceDetails]
  def counts(flowDefinitionId: Option[Seq[String]], status: Option[Seq[String]]): Future[Seq[InstanceCount]]
}

