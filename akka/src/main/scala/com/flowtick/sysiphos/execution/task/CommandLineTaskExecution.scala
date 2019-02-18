package com.flowtick.sysiphos.execution.task

import java.io.{ File, FileOutputStream }

import cats.effect.{ ContextShift, IO }
import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.core.Clock
import com.flowtick.sysiphos.execution.FlowTaskExecution
import com.flowtick.sysiphos.flow.{ FlowInstanceContextValue, FlowTaskInstance }
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId
import fs2.Stream

import scala.concurrent.ExecutionContext
import scala.sys.process._
import scala.util.Try

trait CommandLineTaskExecution extends FlowTaskExecution with Clock {
  implicit val executionContext: ExecutionContext

  implicit lazy val contextShift: ContextShift[IO] = cats.effect.IO.contextShift(executionContext)

  def createScriptFile(taskInstance: FlowTaskInstance, command: String): File = {
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
    contextValues: Seq[FlowInstanceContextValue],
    command: String): Try[String] = {
    val creationDateTime = fromEpochSeconds(taskInstance.creationTime)
    val additionalModel = sanitizedSysProps ++ Map("creationTime" -> creationDateTime)

    replaceContextInTemplate(command, contextValues, additionalModel)
  }

  protected def commandIO(
    command: String,
    logId: LogId)(taskLogger: Logger): IO[Int] = {
    for {
      outputQueue <- Stream.eval(fs2.concurrent.Queue.synchronous[IO, Option[String]])
      result <- Stream
        .eval(IO(command.!(ProcessLogger(line => outputQueue.enqueue1(Some(line)).unsafeRunSync()))))
        .concurrently(Stream.eval(taskLogger.appendStream(logId, outputQueue.dequeue.unNoneTerminate)))
    } yield result
  }.compile.lastOrError

  def runCommand(
    taskInstance: FlowTaskInstance,
    command: String,
    shellOption: Option[String])(taskLogger: Logger): IO[Int] = IO.unit.flatMap { _ =>
    val scriptFile = createScriptFile(taskInstance, command)

    val commandLine = shellOption.map { shell =>
      s"$shell ${scriptFile.getAbsolutePath}"
    }.getOrElse(command)

    for {
      _ <- taskLogger.appendLine(taskInstance.logId, s"\n### running '$command' via '$commandLine'")
      exitCode <- commandIO(commandLine, taskInstance.logId)(taskLogger).guarantee(IO(scriptFile.delete()))
      result <- {
        if (exitCode == 0) IO.pure(exitCode)
        else IO.raiseError(new RuntimeException(s"😞 got failure code during execution: $exitCode"))
      }
    } yield result
  }
}
