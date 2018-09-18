package com.flowtick.sysiphos.execution

import java.time.LocalDateTime.ofEpochSecond
import java.time.ZoneOffset

import akka.actor.Actor
import com.flowtick.sysiphos.flow.{ FlowInstance, FlowTaskInstance }
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId
import com.flowtick.sysiphos.task.CommandLineTask

import scala.sys.process._
import scala.util.{ Failure, Success, Try }

class FlowTaskExecutionActor(
  taskInstance: FlowTaskInstance,
  flowInstance: FlowInstance) extends Actor with FlowTaskExecution with Logging {

  val logger: Logger = Logger.defaultLogger

  def writeToLog(logId: LogId)(line: String): Try[Unit] = logger.appendToLog(logId, Seq(line))

  def replaceContext(command: String): Try[String] = {
    val creationDateTime = ofEpochSecond(taskInstance.creationTime, 0, ZoneOffset.UTC)
    val additionalModel = sanitizedSysProps ++ Map("creationTime" -> creationDateTime)

    replaceContextInTemplate(command, flowInstance.context, additionalModel)
  }

  def runCommand(command: String)(log: String => Try[Unit]): Try[Int] = Try {
    val taskLogHeader =
      s"""### running $command , retries left: ${taskInstance.retries}""".stripMargin

    log(taskLogHeader)
    val exitCode = command.!(ProcessLogger(log(_), log(_)))
    log(s"\n### command finished with exit code $exitCode")
    exitCode
  }.filter(_ == 0)

  override def receive: Receive = {
    case FlowTaskExecution.Execute(CommandLineTask(id, _, command), logId) =>
      log.info(s"executing command with id $id")

      val taskLogger = writeToLog(logId) _

      val tryRun: Try[Int] = for {
        finalCommand <- replaceContext(command)
        result <- runCommand(finalCommand)(taskLogger)
      } yield result

      tryRun match {
        case Failure(e) =>
          taskLogger(e.getMessage)
          sender() ! FlowInstanceExecution.WorkFailed(e, taskInstance)
        case Success(_) => sender() ! FlowInstanceExecution.WorkDone(taskInstance)
      }
    case other: Any => sender() ! FlowInstanceExecution.WorkFailed(new IllegalStateException(s"unable to handle $other"), taskInstance)
  }
}
