package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.core.RepositoryContext

import scala.concurrent.Future

case class FlowTaskInstanceQuery(flowInstanceId: String)

trait FlowTaskInstanceRepository {
  def getFlowTaskInstances(query: FlowTaskInstanceQuery)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowTaskInstance]]
}
