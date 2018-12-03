package com.flowtick.sysiphos.execution

import java.util.concurrent.Executors

import akka.NotUsed
import akka.actor.{ Actor, ActorRef, Props }
import akka.pattern.pipe
import akka.routing.BalancingPool
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.stream.{ ActorMaterializer, KillSwitches }
import cats.data.OptionT
import cats.instances.future._
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowInstanceExecution._
import com.flowtick.sysiphos.execution.FlowTaskExecution.Execute
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.logging.Logger
import kamon.Kamon

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.Try

class FlowInstanceExecutorActor(
  flowDefinition: FlowDefinition,
  flowInstance: FlowInstance,
  val flowInstanceRepository: FlowInstanceRepository,
  val flowTaskInstanceRepository: FlowTaskInstanceRepository,
  logger: Logger)(implicit repositoryContext: RepositoryContext)
  extends Actor with FlowInstanceExecution {
  import Logging._

  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.fromExecutor(Executors.newWorkStealingPool())
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

  val taskExecutorProps = Props(new FlowTaskExecutionActor(flowInstance, self, context.parent, logger))

  val taskParallelism: Int = flowDefinition.taskParallelism.getOrElse(1)

  protected lazy val taskActorPool: ActorRef = {
    context.actorOf(BalancingPool(taskParallelism).props(taskExecutorProps))
  }

  def taskActorSink(targetActor: ActorRef): Sink[Any, NotUsed] =
    Sink.actorRefWithAck(
      targetActor,
      onInitMessage = FlowTaskExecution.TaskStreamInitialized,
      ackMessage = FlowTaskExecution.TaskAck,
      onCompleteMessage = FlowTaskExecution.TaskStreamCompleted,
      onFailureMessage = (ex: Throwable) => FlowTaskExecution.TaskStreamFailure(ex))

  protected lazy val ((queue, killSwitch), done) =
    Source
      .queue[FlowTask](taskParallelism, akka.stream.OverflowStrategy.backpressure)
      .throttle(flowDefinition.taskRatePerSecond.getOrElse(1), 1.second)
      .viaMat(KillSwitches.single)(Keep.both)
      .mapAsync(parallelism = taskParallelism)(flowTask => {
        getOrCreateTaskInstance(flowInstance, flowTask, logger)
          .map(taskInstance => Execute(flowTask, taskInstance))
          .logFailed("unable to get or create task instance")
      })
      .filter(execute => isRunnable(execute.taskInstance))
      .filter(execute => execute.taskInstance.nextDueDate.forall(fromEpochSeconds(_).isBefore(now.toLocalDateTime)))
      .mapAsync(parallelism = taskParallelism)(setRunning(_, logger).logFailed("unable to set running"))
      .wireTap(execute => log.debug(s"passing from stream to actor $execute"))
      .toMat(taskActorSink(taskActorPool))(Keep.both)
      .run

  override def postStop(): Unit = {
    log.debug("shutting down task stream")
    Try(killSwitch.shutdown()).failed.foreach(error => log.warn(s"unable to shutdown task stream, ${error.getMessage}"))
  }

  def enqueueNextTasks(
    taskSelection: FlowTaskSelection,
    currentInstances: Seq[FlowTaskInstanceDetails]): Future[Seq[Either[Throwable, FlowTask]]] =
    for {
      next <- Future.successful(nextFlowTasks(
        taskSelection,
        flowDefinition,
        currentInstances))

      enqueued <- Future.sequence(next.map { flowTask =>
        queue.offer(flowTask).flatMap[Either[Throwable, FlowTask]] {
          case Enqueued => Future.successful(Right(flowTask))
          case other => Future.successful(Left(new RuntimeException(s"unable to qneue $flowTask, $other")))
        }.recoverWith {
          case error => Future.successful(Left(error))
        }
      })
    } yield enqueued

  override def receive: PartialFunction[Any, Unit] = {
    case FlowInstanceExecution.Execute(taskSelection) =>
      log.info(s"executing ${flowDefinition.id} instance ${flowInstance.id} ...")
      (for {
        currentInstances <- flowTaskInstanceRepository.find(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id)))

        enqueueResult <- enqueueNextTasks(taskSelection, currentInstances).logSuccess(result => s"new in queue: $result")

        executionResult <- if (enqueueResult.exists(_.isRight) || currentInstances.exists(isPending)) {
          Future.successful(WorkPending(flowInstance))
        } else if (currentInstances.forall(_.status == FlowTaskInstanceStatus.Done)) {
          Future.successful(Finished(flowInstance))
        } else {
          Future.successful(ExecutionFailed(flowInstance))
        }

      } yield executionResult)
        .logSuccess(executionResult => s"execution result $executionResult")
        .logFailed(s"unable to execute $flowInstance").pipeTo(context.parent)

    case FlowInstanceExecution.WorkFailed(error, flowTaskInstance) =>
      Kamon.counter("work-failed").refine(
        ("definition", flowDefinition.id),
        ("task", flowTaskInstance.taskId)).increment()

      log.warn(s"task ${flowTaskInstance.id} failed with ${error.getLocalizedMessage}")

      val handledFailure = for {
        _ <- flowTaskInstanceRepository.setEndTime(flowTaskInstance.id, repositoryContext.epochSeconds)
        handled <- handleFailedTask(flowTaskInstance, error)
      } yield handled

      handledFailure.logFailed(s"unable to handle failure in ${flowTaskInstance.id}").pipeTo(context.parent)

    case FlowInstanceExecution.WorkDone(flowTaskInstance, addToContext) =>
      log.info(s"Work is done for task with id ${flowTaskInstance.taskId}.")

      Kamon.counter("work-done").refine(
        ("definition", flowDefinition.id),
        ("task", flowTaskInstance.taskId)).increment()

      val doneTaskInstance = for {
        _ <- flowTaskInstanceRepository.setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Done)
        ended <- flowTaskInstanceRepository.setEndTime(flowTaskInstance.id, repositoryContext.epochSeconds)
        _ <- flowInstanceRepository.insertOrUpdateContextValues(flowTaskInstance.flowInstanceId, addToContext)
      } yield ended

      OptionT(doneTaskInstance)
        .getOrElseF(Future.failed(new IllegalStateException("flow task instance not updated")))
        .map(TaskCompleted)
        .pipeTo(context.parent)
        .logFailed(s"unable to complete task ${flowTaskInstance.id}")
        .foreach(_ => Kamon.counter("task-completed").refine(
          ("definition", flowDefinition.id),
          ("task", flowTaskInstance.taskId)).increment())

    case other => log.warn(s"unhandled message: $other")
  }

  private def handleFailedTask(flowTaskInstance: FlowTaskInstance, error: Throwable): Future[FlowInstanceMessage] =
    if (flowTaskInstance.retries == 0) {
      log.error(s"retries exceeded for $flowTaskInstance, failed execution", error)
      flowTaskInstanceRepository
        .setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Failed)
        .map(_ => FlowInstanceExecution.ExecutionFailed(flowInstance))
    } else {
      val dueDate = repositoryContext.epochSeconds + flowTaskInstance.retryDelay
      log.info(s"scheduling retry for ${flowTaskInstance.id} for $dueDate")

      for {
        _ <- flowTaskInstanceRepository.setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Retry)
        _ <- flowTaskInstanceRepository.setRetries(flowTaskInstance.id, flowTaskInstance.retries - 1)
        _ <- flowTaskInstanceRepository.setNextDueDate(flowTaskInstance.id, Some(dueDate))
      } yield RetryScheduled(flowTaskInstance)
    }
}

