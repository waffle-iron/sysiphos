package com.flowtick.sysiphos.execution

import java.io.{ File, FileOutputStream }
import java.time.LocalDateTime.ofEpochSecond
import java.time.ZoneOffset
import java.util.concurrent.{ Executors, TimeUnit }

import akka.actor.{ Actor, ActorRef }
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import cats.effect.{ ContextShift, IO }
import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.execution.FlowExecutorActor.{ NewInstance, RequestInstance }
import com.flowtick.sysiphos.flow.{ FlowInstance, FlowTaskInstance }
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.logging.Logger.LogId
import com.flowtick.sysiphos.task.{ CommandLineTask, TriggerFlowTask }

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.sys.process._
import scala.util.{ Failure, Success, Try }

class FlowTaskExecutionActor(
  taskInstance: FlowTaskInstance,
  flowInstance: FlowInstance,
  flowExecutorActor: ActorRef) extends Actor with FlowTaskExecution with Logging {

  val taskLogger: Logger = Logger.defaultLogger

  implicit val taskExecutionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newWorkStealingPool())

  def replaceContext(command: String): Try[String] = {
    val creationDateTime = ofEpochSecond(taskInstance.creationTime, 0, ZoneOffset.UTC)
    val additionalModel = sanitizedSysProps ++ Map("creationTime" -> creationDateTime)

    replaceContextInTemplate(command, flowInstance.context, additionalModel)
  }

  private def commandIO(command: String, logId: LogId, logQueue: fs2.concurrent.Queue[IO, Option[String]]): IO[Int] = IO.async[Int] { callback =>
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

  def runCommand(command: String, shellOption: Option[String], logId: LogId): Try[Int] = Try {
    import IO._
    implicit val contextShift: ContextShift[IO] = cats.effect.IO.contextShift(taskExecutionContext)

    val taskLogHeader =
      s"""### running $command , retries left: ${taskInstance.retries}""".stripMargin

    taskLogger.appendLine(logId, taskLogHeader).unsafeRunSync()

    val commandLine = shellOption.map { shell =>
      s"$shell ${createScriptFile(command, shell).getAbsolutePath}"
    }.getOrElse(command)

    val queueSize = Configuration.propOrEnv("logger.stream.queueSize", "1000").toInt

    val finishedProcess: IO[Int] = fs2.concurrent.Queue
      .circularBuffer[IO, Option[String]](queueSize)
      .flatMap(commandIO(commandLine, logId, _))

    finishedProcess.unsafeRunSync()
  }.flatMap { exitCode =>
    taskLogger.appendLine(logId, s"\n### command finished with exit code $exitCode").unsafeRunSync()
    if (exitCode != 0) {
      Failure(new RuntimeException(s"😞 got failure code during execution: $exitCode"))
    } else Success(exitCode)
  }

  def createScriptFile(command: String, shell: String): File = {
    val tempDir = sys.props.get("java.io.tempdir").getOrElse(Configuration.propOrEnv("backup.temp.dir", "/tmp"))
    val scriptFile = new File(tempDir, s"script_${taskInstance.id}.sh")
    val scriptOutput = new FileOutputStream(scriptFile)

    scriptOutput.write(command.getBytes("UTF-8"))
    scriptOutput.flush()
    scriptOutput.close()
    scriptFile
  }

  override def receive: Receive = {
    case FlowTaskExecution.Execute(CommandLineTask(id, _, command, _, shell), logId) =>
      log.info(s"executing command with id $id")

      val tryRun: Try[Int] = for {
        finalCommand <- replaceContext(command)
        result <- runCommand(finalCommand, shell, logId)
      } yield result

      tryRun match {
        case Failure(e) =>
          taskLogger.appendLine(logId, e.getMessage).unsafeRunSync()
          sender() ! FlowInstanceExecution.WorkFailed(e, taskInstance)
        case Success(_) => sender() ! FlowInstanceExecution.WorkDone(taskInstance)
      }

    case FlowTaskExecution.Execute(TriggerFlowTask(id, _, flowDefinitionId, _), logId) =>
      log.info(s"executing task with id $id")

      ask(flowExecutorActor, RequestInstance(flowDefinitionId, flowInstance.context))(Timeout(30, TimeUnit.SECONDS)).map {
        case NewInstance(Right(instance)) =>
          taskLogger.appendLine(logId, s"created ${instance.flowDefinitionId} instance ${instance.id}").unsafeRunSync()
          FlowInstanceExecution.WorkDone(taskInstance)
        case NewInstance(Left(error)) =>
          taskLogger.appendLine(logId, s"😞 unable to trigger instance $flowDefinitionId: ${error.getMessage}").unsafeRunSync()
          FlowInstanceExecution.WorkFailed(error, taskInstance)
      }.pipeTo(sender())

    case other: Any => sender() ! FlowInstanceExecution.WorkFailed(new IllegalStateException(s"unable to handle $other"), taskInstance)
  }

}
