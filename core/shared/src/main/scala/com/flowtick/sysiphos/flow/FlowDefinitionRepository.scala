package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.core.RepositoryContext

import scala.concurrent.Future

trait FlowDefinitionRepository {
  def addFlowDefinition(flowDefinition: FlowDefinition)(implicit repositoryContext: RepositoryContext): Future[FlowDefinition]
  def getFlowDefinitions(implicit repositoryContext: RepositoryContext): Future[Seq[FlowDefinition]]
}