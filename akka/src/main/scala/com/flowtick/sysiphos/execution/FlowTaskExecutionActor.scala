package com.flowtick.sysiphos.execution

import java.io.ByteArrayInputStream

import akka.actor.Actor
import com.flowtick.sysiphos.flow.{ FlowTask, FlowTaskInstance }
import com.flowtick.sysiphos.logging.{ FileLogger, Logger }
import com.flowtick.sysiphos.logging.Logger.LogId
import com.flowtick.sysiphos.task.CommandLineTask

import scala.sys.process._
import scala.util.{ Failure, Success, Try }

class FlowTaskExecutionActor(
  taskInstance: FlowTaskInstance,
  logId: LogId) extends Actor with Logging {

  val logger: FileLogger = Logger.defaultLogger

  def writeToLog(line: String): Try[Unit] = logger.appendToLog(logId, Seq(line))

  override def receive: Receive = {
    case FlowTaskExecution.Execute(CommandLineTask(id, _, command)) =>
      log.info(s"executing command with id $id")

      val taskLogHeader =
        s"""=============================================
           |running $command, retries left: ${taskInstance.retries}
           |=============================================
         """.stripMargin

      writeToLog(taskLogHeader)

      val result: Try[Int] = Try {
        val exitCode = command.!(ProcessLogger(writeToLog, writeToLog))
        writeToLog(s"\ncommand finished with exit code $exitCode")
        exitCode
      }.filter(_ == 0)

      result match {
        case Failure(e) =>
          writeToLog(e.getMessage)
          sender() ! FlowInstanceExecution.WorkFailed(e, taskInstance)
        case Success(_) => sender() ! FlowInstanceExecution.WorkDone(taskInstance)
      }
    case other: Any => sender() ! FlowInstanceExecution.WorkFailed(new IllegalStateException(s"unable to handle $other"), taskInstance)
  }
}

object FlowTaskExecution {
  case class Execute(flowTask: FlowTask)
}

