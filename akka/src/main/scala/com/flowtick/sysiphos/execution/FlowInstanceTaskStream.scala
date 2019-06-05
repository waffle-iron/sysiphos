package com.flowtick.sysiphos.execution

import java.util.UUID

import akka.NotUsed
import akka.actor.{ ActorRef, ActorSystem, PoisonPill, Props }
import akka.stream.scaladsl.{ Keep, RunnableGraph, Sink, Source, SourceQueueWithComplete }
import akka.stream.{ KillSwitches, UniqueKillSwitch }
import cats.data.OptionT
import cats.effect.{ ContextShift, IO, Timer }
import cats.syntax.all._
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.Logging._
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.logging.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

trait FlowInstanceTaskStream { taskStream: FlowInstanceExecution =>

  implicit val executionContext: ExecutionContext
  implicit val actorSystem: ActorSystem

  protected def taskActorPool(
    flowInstanceActor: ActorRef,
    flowExecutorActor: ActorRef,
    poolSize: Int,
    logger: Logger): ActorRef = {
    actorSystem.actorOf(Props(new FlowTaskExecutionActor(flowInstanceActor, flowExecutorActor, logger)), "taskWorker-" + UUID.randomUUID().toString)
  }

  protected def taskStreamSink(sinkActor: ActorRef): Sink[Any, NotUsed] =
    Sink.actorRefWithAck(
      sinkActor,
      onInitMessage = FlowTaskExecution.TaskStreamInitialized,
      ackMessage = FlowTaskExecution.TaskAck,
      onCompleteMessage = PoisonPill,
      onFailureMessage = (ex: Throwable) => FlowTaskExecution.TaskStreamFailure(ex))

  def createTaskStream(
    flowTaskInstanceRepository: FlowTaskInstanceRepository,
    flowInstanceRepository: FlowInstanceRepository,
    flowInstanceActor: ActorRef,
    flowExecutorActor: ActorRef)(
    flowInstanceId: String,
    flowDefinitionId: String,
    taskParallelism: Int,
    taskRate: Int,
    taskRateDuration: FiniteDuration,
    logger: Logger)(implicit repositoryContext: RepositoryContext, timer: Timer[IO], cs: ContextShift[IO]): RunnableGraph[((SourceQueueWithComplete[FlowTask], UniqueKillSwitch), NotUsed)] =
    Source
      .queue[FlowTask](0, akka.stream.OverflowStrategy.backpressure)
      .throttle(taskRate, taskRateDuration)
      .viaMat(KillSwitches.single)(Keep.both)
      .mapAsync[FlowTaskExecution.Execute](parallelism = taskParallelism)(flowTask => {
        (for {
          freshFlowInstance <- OptionT[IO, FlowInstanceDetails](IO.fromFuture(IO(flowInstanceRepository.findById(flowInstanceId))))
          contextValues <- OptionT.liftF(IO.fromFuture(IO(flowInstanceRepository.getContextValues(freshFlowInstance.id))))
          newTaskInstance <- OptionT.liftF(getOrCreateTaskInstance(flowTaskInstanceRepository, flowInstanceId, flowDefinitionId, flowTask, logger))
        } yield FlowTaskExecution.Execute(flowTask, newTaskInstance, contextValues))
          .getOrElseF(IO.raiseError(new IllegalStateException("unable to find instance")))
          .unsafeToFuture()
          .logFailed("unable to create new task instance")
      })
      .filter(execute => isRunnable(execute.taskInstance))
      .filter(execute => execute.taskInstance.nextDueDate.forall(fromEpochSeconds(_).isBefore(currentTime)))
      .mapAsync(parallelism = taskParallelism)((execute: FlowTaskExecution.Execute) => setRunning(flowTaskInstanceRepository, flowInstanceRepository, execute, logger)
        .timeout(5.seconds)
        .handleErrorWith(error => {
          IO.fromFuture(IO(flowTaskInstanceRepository.setStatus(execute.taskInstance.id, FlowTaskInstanceStatus.Failed, Some(0), None)(repositoryContext))) *>
            IO.fromFuture(IO(flowInstanceRepository.update(FlowInstanceQuery.byId(execute.taskInstance.flowInstanceId), FlowInstanceStatus.Failed, Some(error)))) *>
            IO.raiseError[FlowTaskExecution.Execute](error)
        })
        .unsafeToFuture()
        .logFailed("unable to set running"))
      .wireTap(execute => log.debug(s"passing from stream to actor $execute"))
      .toMat(taskStreamSink(taskActorPool(flowInstanceActor, flowExecutorActor, taskParallelism, logger)))(Keep.both)

}
