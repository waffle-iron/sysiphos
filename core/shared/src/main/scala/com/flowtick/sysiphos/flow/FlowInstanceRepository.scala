package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.core.RepositoryContext

import scala.concurrent.Future

case class FlowInstanceQuery(flowDefinitionId: String)

trait FlowInstanceRepository[T <: FlowInstance] {
  def getFlowInstances(query: FlowInstanceQuery)(implicit repositoryContext: RepositoryContext): Future[Seq[T]]
  def createFlowInstance(flowDefinitionId: String, context: Map[String, String])(implicit repositoryContext: RepositoryContext): Future[T]
}

