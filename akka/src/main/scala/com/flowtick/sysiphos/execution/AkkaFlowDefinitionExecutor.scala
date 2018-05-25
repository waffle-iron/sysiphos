package com.flowtick.sysiphos.execution

import akka.actor.Actor
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowTask }
import com.flowtick.sysiphos.task.CommandLineTask

import scala.sys.process._
trait FlowDefinitionExecutor extends Logging {
  val flowDefinition: FlowDefinition

  def execute(task: FlowTask): Unit = task match {
    case CommandLineTask(id, children, command) =>
      log.info(s"executing command with id $id")
      val result = { command !! }
      log.info(result)
      children.getOrElse(List.empty).foreach(execute)
    case _ => log.error("i have no idea how to execut this task")
  }
}

class AkkaFlowDefinitionExecutor(override val flowDefinition: FlowDefinition)
  extends Actor with FlowDefinitionExecutor {

  override def receive: PartialFunction[Any, Unit] = {
    case FlowDefinitionExecutor.Init => execute(flowDefinition.task)
  }
}

object FlowDefinitionExecutor {
  case object Init
}
