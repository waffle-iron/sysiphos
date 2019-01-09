package com.flowtick.sysiphos.execution

import java.util.concurrent.Executors

import akka.actor.{ Actor, ActorSystem, Props }
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.QueueOfferResult.Enqueued
import cats.data.OptionT
import cats.instances.future._
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowInstanceExecution._
import com.flowtick.sysiphos.execution.FlowTaskExecution.TaskStreamFailure
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.logging.Logger
import kamon.Kamon

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class FlowInstanceExecutorActor(
  flowInstanceId: String,
  flowDefinition: FlowDefinition,
  val flowInstanceRepository: FlowInstanceRepository,
  val flowTaskInstanceRepository: FlowTaskInstanceRepository,
  logger: Logger)(implicit repositoryContext: RepositoryContext)
  extends Actor with FlowInstanceExecution with FlowInstanceTaskStream {
  import Logging._

  implicit val actorSystem: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.fromExecutor(Executors.newWorkStealingPool())
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val taskExecutorProps = Props(new FlowTaskExecutionActor(self, context.parent, logger))

  val taskParallelism: Int = flowDefinition.taskParallelism.getOrElse(1)

  protected lazy val ((queue, killSwitch), done) = createTaskStream(self, context.parent)(
    flowInstanceId,
    flowDefinition.id,
    taskParallelism,
    flowDefinition.taskRatePerSecond.getOrElse(1),
    1.seconds,
    logger)(repositoryContext).run

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
      log.info(s"executing ${flowDefinition.id} instance $flowInstanceId...")
      (for {
        currentInstances <- flowTaskInstanceRepository.find(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstanceId)))

        enqueueResult <- enqueueNextTasks(taskSelection, currentInstances).logSuccess(result => s"new in queue: $result")

        executionResult <- if (enqueueResult.exists(_.isRight) || currentInstances.exists(isPending)) {
          Future.successful(WorkPending(flowInstanceId))
        } else if (currentInstances.forall(_.status == FlowTaskInstanceStatus.Done)) {
          Future.successful(Finished(flowInstanceId, flowDefinition.id))
        } else {
          Future.successful(ExecutionFailed(flowInstanceId, flowDefinition.id))
        }

      } yield executionResult)
        .logSuccess(executionResult => s"execution result $executionResult")
        .logFailed(s"unable to execute $flowInstanceId").pipeTo(context.parent)

    case FlowInstanceExecution.WorkFailed(error, optionalTaskInstance) => optionalTaskInstance match {
      case Some(flowTaskInstance) =>
        Kamon.counter("work-failed").refine(
          ("definition", flowDefinition.id),
          ("task", flowTaskInstance.taskId)).increment()

        val taskFailedMessage = s"task ${flowTaskInstance.id} failed with ${error.getLocalizedMessage}"

        log.warn(taskFailedMessage)

        logger.appendLine(flowTaskInstance.logId, taskFailedMessage).unsafeRunSync()

        val handledFailure = for {
          _ <- flowTaskInstanceRepository.setEndTime(flowTaskInstance.id, repositoryContext.epochSeconds)
          handled <- handleFailedTask(flowTaskInstance, error)
        } yield handled

        handledFailure.logFailed(s"unable to handle failure in ${flowTaskInstance.id}").pipeTo(context.parent)
      case None =>
        log.error("error during task execution", error)
    }

    case FlowInstanceExecution.WorkDone(flowTaskInstance, addToContext) =>
      log.info(s"Work is done for task with id ${flowTaskInstance.taskId}.")

      Kamon.counter("work-done").refine(
        ("definition", flowDefinition.id),
        ("task", flowTaskInstance.taskId)).increment()

      val doneTaskInstance = for {
        _ <- flowTaskInstanceRepository.setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Done, None, None)
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

    case TaskStreamFailure(error) =>
      log.error("error in task stream", error)
      self ! FlowInstanceExecution.Execute(PendingTasks)

    case other => log.warn(s"unhandled message: $other")
  }

  private def handleFailedTask(flowTaskInstance: FlowTaskInstance, error: Throwable): Future[FlowInstanceMessage] =
    if (flowTaskInstance.retries == 0) {
      log.error(s"retries exceeded for $flowTaskInstance, failed execution", error)
      flowTaskInstanceRepository
        .setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Failed, None, None)
        .map(_ => FlowInstanceExecution.ExecutionFailed(flowInstanceId, flowDefinition.id))
    } else {
      val dueDate = repositoryContext.epochSeconds + flowTaskInstance.retryDelay
      log.info(s"scheduling retry for ${flowTaskInstance.id} for $dueDate")

      for {
        _ <- flowTaskInstanceRepository.setStatus(
          flowTaskInstance.id,
          FlowTaskInstanceStatus.Retry,
          Some(flowTaskInstance.retries - 1),
          Some(dueDate))
      } yield RetryScheduled(flowTaskInstance)
    }
}

