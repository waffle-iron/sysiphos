package com.flowtick.sysiphos.execution

import java.util.concurrent.{ Executors, TimeUnit }

import akka.actor.{ Actor, ActorRef }
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import cats.effect.IO
import com.flowtick.sysiphos.execution.FlowExecutorActor.{ NewInstance, RequestInstance }
import com.flowtick.sysiphos.execution.FlowTaskExecution.{ TaskAck, TaskStreamCompleted, TaskStreamFailure, TaskStreamInitialized }
import com.flowtick.sysiphos.execution.task.{ CamelTaskExecution, CommandLineTaskExecution }
import com.flowtick.sysiphos.flow.FlowInstance
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.task.{ CamelTask, CommandLineTask, TriggerFlowTask }

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success, Try }

class FlowTaskExecutionActor(
  flowInstance: FlowInstance,
  flowInstanceActor: ActorRef,
  flowExecutorActor: ActorRef,
  taskLogger: Logger) extends Actor
  with CommandLineTaskExecution
  with CamelTaskExecution
  with Logging {

  implicit val taskExecutionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newWorkStealingPool())

  override def receive: Receive = {
    case FlowTaskExecution.Execute(CommandLineTask(id, _, command, _, shell, _, _, _), taskInstance) =>
      log.info(s"executing command with id $id")

      val run: IO[Int] = for {
        finalCommand <- replaceContext(taskInstance, flowInstance, command) match {
          case Success(value) => IO.pure(value)
          case Failure(error) => IO.raiseError(error)
        }
        result <- runCommand(taskInstance, finalCommand, shell, taskInstance.logId)(taskLogger)
      } yield result

      run.unsafeToFuture().map { exitCode =>
        taskLogger.appendLine(taskInstance.logId, s"\n### command finished successfully with exit code $exitCode").unsafeRunSync()

        FlowInstanceExecution.WorkDone(taskInstance)
      }.recoverWith {
        case error =>
          taskLogger.appendLine(taskInstance.logId, s"error in command execution: ${error.getMessage}").unsafeRunSync()
          Future.successful(FlowInstanceExecution.WorkFailed(error, taskInstance))
      }.pipeTo(flowInstanceActor)

      sender() ! TaskAck

    case FlowTaskExecution.Execute(camelTask: CamelTask, taskInstance) =>
      executeExchange(camelTask, flowInstance.context, taskInstance.logId)(taskLogger)
        .attempt
        .unsafeToFuture()
        .map {
          case Left(error) =>
            taskLogger.appendLine(taskInstance.logId, s"error in camel exchange: ${error.getMessage}").unsafeRunSync()
            Future.successful(FlowInstanceExecution.WorkFailed(error, taskInstance))

          case Right((exchange, contextValues)) =>
            val resultString = Try(exchange.getOut.getBody(classOf[String])).getOrElse(exchange.getOut.toString)

            taskLogger.appendLine(taskInstance.logId, s"camel exchange executed with result: $resultString").unsafeRunSync()
            FlowInstanceExecution.WorkDone(taskInstance, contextValues)
        }.recoverWith {
          case error =>
            taskLogger.appendLine(taskInstance.logId, s"error in camel exchange: ${error.getMessage}").unsafeRunSync()
            Future.successful(FlowInstanceExecution.WorkFailed(error, taskInstance))
        }.pipeTo(flowInstanceActor)

      sender() ! TaskAck

    case FlowTaskExecution.Execute(TriggerFlowTask(id, _, flowDefinitionId, _, _, _, _), taskInstance) =>
      log.info(s"executing task with id $id")

      ask(flowExecutorActor, RequestInstance(flowDefinitionId, flowInstance.context))(Timeout(30, TimeUnit.SECONDS)).map {
        case NewInstance(Right(instance)) =>
          taskLogger.appendLine(taskInstance.logId, s"created ${instance.flowDefinitionId} instance ${instance.id}").unsafeRunSync()
          FlowInstanceExecution.WorkDone(taskInstance)
        case NewInstance(Left(error)) =>
          taskLogger.appendLine(taskInstance.logId, s"😞 unable to trigger instance $flowDefinitionId: ${error.getMessage}").unsafeRunSync()
          FlowInstanceExecution.WorkFailed(error, taskInstance)
      }.pipeTo(flowInstanceActor)

      sender() ! TaskAck

    case TaskStreamInitialized =>
      log.info("stream initialized")
      sender() ! TaskAck

    case TaskStreamCompleted =>
      log.info("stream completed")

    case streamError: TaskStreamFailure =>
      flowInstanceActor ! streamError

    case other: Any =>
      log.error(s"unable to handle $other, this is not recoverable, failing execution")
      flowExecutorActor ! FlowInstanceExecution.ExecutionFailed(flowInstance)
  }

}
