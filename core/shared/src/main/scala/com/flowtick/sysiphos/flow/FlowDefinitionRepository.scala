package com.flowtick.sysiphos.flow

import scala.concurrent.Future

trait FlowDefinitionRepository {
  def addFlowDefinition(flowDefinition: FlowDefinition): Future[FlowDefinition]
  def getFlowDefinitions: Future[Seq[FlowDefinition]]
}