package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.core.RepositoryContext

import scala.concurrent.Future

trait FlowTaskInstanceRepository[T <: FlowTaskInstance] {
  def findById(id: String)(implicit repositoryContext: RepositoryContext): Future[Option[T]]
  def getFlowTaskInstances(flowInstanceId: String)(implicit repositoryContext: RepositoryContext): Future[Seq[T]]
  def createFlowTaskInstance(flowInstanceId: String, flowTaskId: String)(implicit repositoryContext: RepositoryContext): Future[T]
  def createFlowTaskInstances(flowInstanceId: String, tasks: Seq[FlowTask])(implicit repositoryContext: RepositoryContext): Future[Seq[FlowTaskInstance]]
  def update(flowInstance: T)(implicit repositoryContext: RepositoryContext): Future[FlowTaskInstance]
  def setStatus(flowTaskInstanceId: String, status: String)(implicit repositoryContext: RepositoryContext): Future[Unit]
  def setRetries(flowTaskInstanceId: String, retries: Int)(implicit repositoryContext: RepositoryContext): Future[Unit]
}
