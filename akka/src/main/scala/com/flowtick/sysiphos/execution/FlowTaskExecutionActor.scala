package com.flowtick.sysiphos.execution

import java.io.{ File, FileOutputStream }
import java.time.LocalDateTime.ofEpochSecond
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, ActorRef }
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.execution.FlowExecutorActor.{ NewInstance, RequestInstance }
import com.flowtick.sysiphos.flow.{ FlowInstance, FlowTaskInstance }
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId
import com.flowtick.sysiphos.task.{ CommandLineTask, TriggerFlowTask }

import scala.sys.process._
import scala.util.{ Failure, Success, Try }
import scala.concurrent.ExecutionContext.Implicits.global

class FlowTaskExecutionActor(
  taskInstance: FlowTaskInstance,
  flowInstance: FlowInstance,
  flowExecutorActor: ActorRef) extends Actor with FlowTaskExecution with Logging {

  val logger: Logger = Logger.defaultLogger

  def writeToLog(logId: LogId)(line: String): Try[Unit] = logger.appendToLog(logId, Seq(line))

  def replaceContext(command: String): Try[String] = {
    val creationDateTime = ofEpochSecond(taskInstance.creationTime, 0, ZoneOffset.UTC)
    val additionalModel = sanitizedSysProps ++ Map("creationTime" -> creationDateTime)

    replaceContextInTemplate(command, flowInstance.context, additionalModel)
  }

  def runCommand(command: String, shellOption: Option[String])(log: String => Try[Unit]): Try[Int] = Try {
    val taskLogHeader =
      s"""### running $command , retries left: ${taskInstance.retries}""".stripMargin

    log(taskLogHeader)

    val commandLine = shellOption.map { shell =>
      s"$shell ${createScriptFile(command, shell).getAbsolutePath}"
    }.getOrElse(command)

    commandLine.!(ProcessLogger(log(_), log(_)))
  }.flatMap { exitCode =>
    log(s"\n### command finished with exit code $exitCode")
    if (exitCode != 0) {
      Failure(new RuntimeException(s"got failure code during execution: $exitCode"))
    } else Success(exitCode)
  }

  def createScriptFile(command: String, shell: String): File = {
    val tempDir = sys.props.get("java.io.tempdir").getOrElse(Configuration.propOrEnv("backup.temp.dir", "/tmp"))
    val scriptFile = new File(tempDir, s"script_${taskInstance.id}.sh")
    val scriptOutput = new FileOutputStream(scriptFile)

    scriptOutput.write(s"#!/bin/$shell\n".getBytes)
    scriptOutput.write(command.getBytes("UTF-8"))
    scriptOutput.flush()
    scriptOutput.close()
    scriptFile
  }

  override def receive: Receive = {
    case FlowTaskExecution.Execute(CommandLineTask(id, _, command, _, shell), logId) =>
      log.info(s"executing command with id $id")

      val taskLogger = writeToLog(logId) _

      val tryRun: Try[Int] = for {
        finalCommand <- replaceContext(command)
        result <- runCommand(finalCommand, shell)(taskLogger)
      } yield result

      tryRun match {
        case Failure(e) =>
          taskLogger(e.getMessage)
          sender() ! FlowInstanceExecution.WorkFailed(e, taskInstance)
        case Success(_) => sender() ! FlowInstanceExecution.WorkDone(taskInstance)
      }

    case FlowTaskExecution.Execute(TriggerFlowTask(id, _, flowDefinitionId, _), logId) =>
      log.info(s"executing task with id $id")

      val taskLogger = writeToLog(logId) _

      ask(flowExecutorActor, RequestInstance(flowDefinitionId, flowInstance.context))(Timeout(30, TimeUnit.SECONDS)).map {
        case NewInstance(Right(instance)) =>
          taskLogger(s"created ${instance.flowDefinitionId} instance ${instance.id}")
          FlowInstanceExecution.WorkDone(taskInstance)
        case NewInstance(Left(error)) =>
          taskLogger(s"unable to trigger instance $flowDefinitionId: ${error.getMessage}")
          FlowInstanceExecution.WorkFailed(error, taskInstance)
      }.pipeTo(sender())

    case other: Any => sender() ! FlowInstanceExecution.WorkFailed(new IllegalStateException(s"unable to handle $other"), taskInstance)
  }

}
