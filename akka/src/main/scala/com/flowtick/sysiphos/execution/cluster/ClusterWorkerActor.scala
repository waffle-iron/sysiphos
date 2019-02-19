package com.flowtick.sysiphos.execution.cluster

import akka.actor.{ Actor, ActorRef, Props }
import com.flowtick.sysiphos.core.DefaultRepositoryContext
import com.flowtick.sysiphos.execution.ClusterContext.ClusterContextProvider
import com.flowtick.sysiphos.execution._
import com.flowtick.sysiphos.logging.Logger

import scala.concurrent.ExecutionContext

class ClusterWorkerActor(
  flowExecutorActor: ActorRef,
  clusterContext: ClusterContextProvider,
  executionContext: ExecutionContext) extends Actor with Logging {
  override def receive: Receive = {
    case FlowInstanceExecution.Execute(flowInstanceId, flowDefinitionId, taskSelection) =>
      context.actorOf(Props(
        new FlowInstanceExecutorActor(flowInstanceId, flowDefinitionId)(
          clusterContext = clusterContext(),
          flowExecutorActor = flowExecutorActor,
          Logger.defaultLogger)(new DefaultRepositoryContext("worker"), executionContext))) ! FlowInstanceExecution.Run(taskSelection)

    case other: Any =>
      log.debug(s"$other")
  }
}
