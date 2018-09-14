package com.flowtick.sysiphos.execution

import akka.actor.Actor
import com.flowtick.sysiphos.flow.{ FlowTask, FlowTaskInstance }
import com.flowtick.sysiphos.task.CommandLineTask

import scala.util.{ Failure, Success, Try }
import sys.process._

class FlowTaskExecutionActor(taskInstance: FlowTaskInstance) extends Actor with Logging {

  override def receive: Receive = {
    case FlowTaskExecution.Execute(CommandLineTask(id, _, command)) =>
      log.info(s"executing command with id $id")
      val result: Try[String] = Try { command.!! }
      result match {
        case Failure(e) => sender() ! FlowInstanceExecution.WorkFailed(e, taskInstance)
        case Success(value) =>
          log.info(value)
          sender() ! FlowInstanceExecution.WorkDone(taskInstance)
      }
    case other: Any => sender() ! FlowInstanceExecution.WorkFailed(new IllegalStateException(s"unable to handle $other"), taskInstance)
  }
}

object FlowTaskExecution {
  case class Execute(flowTask: FlowTask)
}

