package com.flowtick.sysiphos.execution.cluster

import akka.actor.{ Actor, ActorRef, Props }
import com.flowtick.sysiphos.core.DefaultRepositoryContext
import com.flowtick.sysiphos.execution.{ FlowInstanceExecution, FlowInstanceExecutorActor, Logging, StaticClusterContext }
import com.flowtick.sysiphos.logging.Logger

class ClusterWorkerActor(flowExecutorActor: ActorRef) extends Actor with Logging {
  override def receive: Receive = {
    case execute: FlowInstanceExecution.Execute =>
      context.actorOf(Props(
        new FlowInstanceExecutorActor(
          StaticClusterContext.instance.get,
          flowExecutorActor = flowExecutorActor,
          Logger.defaultLogger)(new DefaultRepositoryContext("worker")))) ! execute

    case other: Any =>
      log.debug(s"$other")
  }
}
