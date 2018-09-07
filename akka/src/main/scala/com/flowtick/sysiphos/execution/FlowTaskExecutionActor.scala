package com.flowtick.sysiphos.execution

import akka.actor.Actor
import com.flowtick.sysiphos.flow.{ FlowTask, FlowTaskInstance }
import com.flowtick.sysiphos.task.CommandLineTask

import scala.util.{ Failure, Success, Try }
import sys.process._

class FlowTaskExecutionActor extends Actor with Logging {

  override def receive: Receive = {
    case execute @ FlowTaskExecution.Execute(CommandLineTask(id, _, command), flowTaskInstance) =>
      log.info(s"executing command with id $id")
      val result: Try[String] = Try { command.!! }
      result match {
        case Failure(e) => sender() ! FlowInstanceExecution.WorkFailed(e, execute.flowTask, flowTaskInstance)
        case Success(value) =>
          log.info(value)
          sender() ! FlowInstanceExecution.WorkDone(flowTaskInstance)
      }
  }
}

object FlowTaskExecution {
  case class Execute(flowTask: FlowTask, flowTaskInstance: FlowTaskInstance)
}

