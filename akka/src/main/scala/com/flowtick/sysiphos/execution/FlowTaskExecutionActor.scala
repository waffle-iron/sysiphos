package com.flowtick.sysiphos.execution

import java.util.concurrent.{ Executors, TimeUnit }

import akka.actor.{ Actor, ActorRef }
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import cats.effect.IO
import com.flowtick.sysiphos.execution.FlowExecutorActor.{ CreatedOrUpdatedDefinition, ImportDefinition, NewInstance, RequestInstance }
import com.flowtick.sysiphos.execution.FlowTaskExecution.{ TaskAck, TaskStreamFailure, TaskStreamInitialized }
import com.flowtick.sysiphos.execution.task.{ CamelTaskExecution, CommandLineTaskExecution, DefinitionImportTaskExecution }
import com.flowtick.sysiphos.flow.FlowDefinition
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.task.{ CamelTask, CommandLineTask, DefinitionImportTask, TriggerFlowTask }

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success, Try }

class FlowTaskExecutionActor(
  flowInstanceActor: ActorRef,
  flowExecutorActor: ActorRef,
  taskLogger: Logger) extends Actor
  with CommandLineTaskExecution
  with CamelTaskExecution
  with DefinitionImportTaskExecution
  with Logging {

  import Logging._

  implicit val taskExecutionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newWorkStealingPool())

  override def receive: Receive = {
    case FlowTaskExecution.Execute(CommandLineTask(id, _, command, _, shell, _, _, _), taskInstance, contextValues) =>
      log.info(s"executing command with id $id")

      val run: IO[Int] = for {
        finalCommand <- replaceContext(taskInstance, contextValues, command) match {
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
          Future.successful(FlowInstanceExecution.WorkFailed(error, Some(taskInstance)))
      }.pipeTo(flowInstanceActor)

      sender() ! TaskAck

    case FlowTaskExecution.Execute(camelTask: CamelTask, taskInstance, contextValues) =>
      executeExchange(camelTask, contextValues, taskInstance.logId)(taskLogger)
        .attempt
        .unsafeToFuture()
        .logFailed(s"unable to execute task $camelTask")
        .map[FlowInstanceExecution.FlowInstanceMessage] {
          case Left(error) =>
            taskLogger.appendLine(taskInstance.logId, s"error in camel exchange: ${error.getMessage}").unsafeRunSync()
            FlowInstanceExecution.WorkFailed(error, Some(taskInstance))

          case Right((exchange, contextValuesFromExpressions)) =>
            val resultString = Try(exchange.getOut.getBody(classOf[String])).getOrElse(exchange.getOut.toString)

            taskLogger.appendLine(taskInstance.logId, s"camel exchange executed with result: $resultString").unsafeRunSync()
            FlowInstanceExecution.WorkDone(taskInstance, contextValuesFromExpressions)
        }.recoverWith {
          case error =>
            taskLogger.appendLine(taskInstance.logId, s"error in camel exchange: ${error.getMessage}").unsafeRunSync()
            Future.successful(FlowInstanceExecution.WorkFailed(error, Some(taskInstance)))
        }.pipeTo(flowInstanceActor)

      sender() ! TaskAck

    case FlowTaskExecution.Execute(TriggerFlowTask(id, _, flowDefinitionId, _, _, _, _), taskInstance, contextValues) =>
      log.info(s"executing task with id $id")

      ask(flowExecutorActor, RequestInstance(flowDefinitionId, contextValues))(Timeout(30, TimeUnit.SECONDS)).map {
        case NewInstance(Right(instanceContext)) =>
          taskLogger.appendLine(taskInstance.logId, s"created ${instanceContext.instance.flowDefinitionId} instance ${instanceContext.instance.id}").unsafeRunSync()
          FlowInstanceExecution.WorkDone(taskInstance)
        case NewInstance(Left(error)) =>
          taskLogger.appendLine(taskInstance.logId, s"😞 unable to trigger instance $flowDefinitionId: ${error.getMessage}").unsafeRunSync()
          FlowInstanceExecution.WorkFailed(error, Some(taskInstance))
      }.pipeTo(flowInstanceActor)

      sender() ! TaskAck

    case FlowTaskExecution.Execute(definitionImportTask: DefinitionImportTask, taskInstance, contextValues) =>
      log.info(s"executing task with id ${definitionImportTask.id}")

      getFlowDefinition(definitionImportTask, contextValues, taskInstance.logId)(taskLogger)
        .unsafeToFuture()
        .logFailed("unable to get flow definition")
        .map[Either[Throwable, FlowDefinition]](Right(_))
        .recoverWith {
          case error => Future.successful(Left(error))
        }
        .flatMap {
          case Right(definition) => ask(flowExecutorActor, ImportDefinition(definition))(Timeout(30, TimeUnit.SECONDS))
          case Left(error) => Future.successful(CreatedOrUpdatedDefinition(Left(error)))
        }.map {
          case CreatedOrUpdatedDefinition(Right(definition)) =>
            taskLogger.appendLine(taskInstance.logId, s"created or updated ${definition.id}").unsafeRunSync()
            FlowInstanceExecution.WorkDone(taskInstance)
          case CreatedOrUpdatedDefinition(Left(error)) =>
            taskLogger.appendLine(taskInstance.logId, s"😞 error while importing flow definition ${definitionImportTask.targetDefinitionId}").unsafeRunSync()
            FlowInstanceExecution.WorkFailed(error, Some(taskInstance))
        }.pipeTo(flowInstanceActor)

      sender() ! TaskAck

    case TaskStreamInitialized =>
      log.info("stream initialized")
      sender() ! TaskAck

    case streamError: TaskStreamFailure =>
      flowInstanceActor ! streamError

    case other: Any =>
      val error = new IllegalStateException(s"unable to handle $other, this is not recoverable, failing execution")
      log.error(error.getMessage, error)
      flowInstanceActor ! FlowInstanceExecution.WorkFailed(error, None)
  }

}
