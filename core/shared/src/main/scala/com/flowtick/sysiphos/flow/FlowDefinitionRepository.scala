package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.core.RepositoryContext

import scala.concurrent.Future

final case class FlowDefinitionMetaData(version: Option[Long], source: Option[String], created: Option[Long])
final case class FlowDefinitionDetails(definition: FlowDefinition, metaData: FlowDefinitionMetaData)

trait FlowDefinitionRepository {
  def addFlowDefinition(flowDefinition: FlowDefinition)(implicit repositoryContext: RepositoryContext): Future[FlowDefinitionDetails]
  def getFlowDefinitions(implicit repositoryContext: RepositoryContext): Future[Seq[FlowDefinitionDetails]]
}