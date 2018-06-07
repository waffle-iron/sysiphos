package com.flowtick.sysiphos.execution

import akka.actor.Actor
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowInstance, FlowInstanceRepository, FlowTask }
import com.flowtick.sysiphos.task.CommandLineTask

import scala.sys.process._
trait FlowInstanceExecution extends Logging {
  val flowDefinition: FlowDefinition
  val flowInstanceRepository: FlowInstanceRepository[FlowInstance]

  def execute(task: FlowTask): Unit = task match {
    case CommandLineTask(id, children, command) =>
      log.info(s"executing command with id $id")
      val result = { command !! }
      log.info(result)
      children.getOrElse(List.empty).foreach(execute)
    case _ => log.error("i have no idea how to execut this task")
  }
}

class FlowInstanceExecutorActor(
  override val flowDefinition: FlowDefinition,
  override val flowInstanceRepository: FlowInstanceRepository[FlowInstance])
  extends Actor with FlowInstanceExecution {

  override def receive: PartialFunction[Any, Unit] = {
    case FlowInstanceExecution.Init => execute(flowDefinition.task)
  }
}

object FlowInstanceExecution {
  case object Init
}
