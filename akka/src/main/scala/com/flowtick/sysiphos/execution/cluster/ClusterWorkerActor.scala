package com.flowtick.sysiphos.execution.cluster

import akka.actor.{ Actor, ActorRef, Props }
import com.flowtick.sysiphos.core.DefaultRepositoryContext
import com.flowtick.sysiphos.execution.{ FlowInstanceExecution, FlowInstanceExecutorActor, Logging, StaticClusterContext }
import com.flowtick.sysiphos.logging.Logger

import scala.concurrent.ExecutionContext

class ClusterWorkerActor(flowExecutorActor: ActorRef, executionContext: ExecutionContext) extends Actor with Logging {
  override def receive: Receive = {
    case execute: FlowInstanceExecution.Execute =>
      context.actorOf(Props(
        new FlowInstanceExecutorActor(
          StaticClusterContext.instance.get,
          flowExecutorActor = flowExecutorActor,
          Logger.defaultLogger)(new DefaultRepositoryContext("worker"), executionContext))) ! execute

    case other: Any =>
      log.debug(s"$other")
  }
}
