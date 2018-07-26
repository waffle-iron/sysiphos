package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.core.RepositoryContext

import scala.concurrent.Future

final case class FlowDefinitionDetails(id: String, version: Option[Long], source: Option[String], created: Option[Long])

trait FlowDefinitionRepository {
  def createOrUpdateFlowDefinition(flowDefinition: FlowDefinition)(implicit repositoryContext: RepositoryContext): Future[FlowDefinitionDetails]
  def getFlowDefinitions(implicit repositoryContext: RepositoryContext): Future[Seq[FlowDefinitionDetails]]
  def findById(id: String)(implicit repositoryContext: RepositoryContext): Future[Option[FlowDefinitionDetails]]
}