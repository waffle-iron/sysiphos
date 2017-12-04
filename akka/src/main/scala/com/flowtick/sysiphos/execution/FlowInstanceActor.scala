package com.flowtick.sysiphos.execution

import akka.actor.Actor
import com.flowtick.sysiphos.execution.FlowInstanceActor.CreateInstance
import com.flowtick.sysiphos.flow.FlowInstanceRepository

object FlowInstanceActor {
  case class CreateInstance(flowDefinitionId: String)
}

class FlowInstanceActor(flowInstanceRepository: FlowInstanceRepository) extends Actor with Logging {
  override def receive = {
    case create: CreateInstance =>
      log.info(s"creating $create")
  }
}
