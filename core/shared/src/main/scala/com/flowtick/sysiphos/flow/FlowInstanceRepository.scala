package com.flowtick.sysiphos.flow

import scala.collection.mutable
import scala.concurrent.Future

case class FlowInstanceQuery(flowDefinitionId: String)

trait FlowInstanceRepository {
  def getFlowInstances(query: FlowInstanceQuery): Future[Seq[FlowInstance]]
}

class InMemoryFlowInstanceRepository extends FlowInstanceRepository {
  val instances = mutable.ListBuffer()
  override def getFlowInstances(query: FlowInstanceQuery): Future[Seq[FlowInstance]] = Future.successful(instances)
}
