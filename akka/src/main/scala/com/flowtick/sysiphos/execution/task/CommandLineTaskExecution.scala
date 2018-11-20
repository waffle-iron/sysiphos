package com.flowtick.sysiphos.execution.task

import java.io.{ File, FileOutputStream }
import java.time.LocalDateTime.ofEpochSecond
import java.time.ZoneOffset

import cats.effect.{ ContextShift, IO }
import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.execution.FlowTaskExecution
import com.flowtick.sysiphos.flow.{ FlowInstance, FlowTaskInstance }
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success, Try }
import scala.sys.process._

trait CommandLineTaskExecution extends FlowTaskExecution {
  implicit val taskExecutionContext: ExecutionContextExecutor

  def createScriptFile(taskInstance: FlowTaskInstance, command: String, shell: String): File = {
    val tempDir = sys.props.get("java.io.tempdir").getOrElse(Configuration.propOrEnv("backup.temp.dir", "/tmp"))
    val scriptFile = new File(tempDir, s"script_${taskInstance.id}.sh")
    val scriptOutput = new FileOutputStream(scriptFile)

    scriptOutput.write(command.getBytes("UTF-8"))
    scriptOutput.flush()
    scriptOutput.close()
    scriptFile
  }

  def replaceContext(
    taskInstance: FlowTaskInstance,
    flowInstance: FlowInstance,
    command: String): Try[String] = {
    val creationDateTime = ofEpochSecond(taskInstance.creationTime, 0, ZoneOffset.UTC)
    val additionalModel = sanitizedSysProps ++ Map("creationTime" -> creationDateTime)

    replaceContextInTemplate(command, flowInstance.context, additionalModel)
  }

  private def commandIO(
    command: String,
    logId: LogId,
    logQueue: fs2.concurrent.Queue[IO, Option[String]])(taskLogger: Logger): IO[Int] = IO.async[Int] { callback =>
    val runningCommand = Future(command.!(ProcessLogger(out => {
      logQueue.enqueue1(Some(out)).unsafeRunSync()
    })))

    runningCommand.foreach(exitCode => {
      logQueue.enqueue1(None).unsafeRunSync()
      callback(Right(exitCode))
    })

    runningCommand.failed.foreach(error => {
      logQueue.enqueue1(None).unsafeRunSync()
      callback(Left(error))
    })

    taskLogger.appendStream(logId, logQueue.dequeue.unNoneTerminate).unsafeRunSync()
  }

  def runCommand(
    taskInstance: FlowTaskInstance,
    command: String,
    shellOption: Option[String],
    logId: LogId)(taskLogger: Logger): Try[Int] = Try {
    import IO._
    implicit val contextShift: ContextShift[IO] = cats.effect.IO.contextShift(taskExecutionContext)

    val commandLine = shellOption.map { shell =>
      s"$shell ${createScriptFile(taskInstance, command, shell).getAbsolutePath}"
    }.getOrElse(command)

    val queueSize = Configuration.propOrEnv("logger.stream.queueSize", "1000").toInt

    val finishedProcess: IO[Int] = fs2.concurrent.Queue
      .circularBuffer[IO, Option[String]](queueSize)
      .flatMap(commandIO(commandLine, logId, _)(taskLogger))

    finishedProcess.unsafeRunSync()
  }.flatMap { exitCode =>
    taskLogger.appendLine(logId, s"\n### command finished with exit code $exitCode").unsafeRunSync()
    if (exitCode != 0) {
      Failure(new RuntimeException(s"😞 got failure code during execution: $exitCode"))
    } else Success(exitCode)
  }
}
