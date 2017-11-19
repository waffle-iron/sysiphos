package com.flowtick.sysiphos.flow

import scala.concurrent.Future

case class FlowInstanceQuery(flowDefinitionId: String)

trait FlowInstanceRepository {
  def getFlowInstances(query: FlowInstanceQuery): Future[Seq[FlowInstance]]
}

