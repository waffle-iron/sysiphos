package com.flowtick.sysiphos.flow

import scala.concurrent.Future

case class FlowTaskInstanceQuery(flowInstanceId: String)

trait FlowTaskInstanceRepository {
  def getFlowTaskInstances(query: FlowTaskInstanceQuery): Future[Seq[FlowTaskInstance]]
}
