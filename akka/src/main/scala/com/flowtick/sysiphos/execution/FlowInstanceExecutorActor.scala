package com.flowtick.sysiphos.execution

import akka.NotUsed
import akka.actor.{ Actor, ActorRef, ActorSystem, PoisonPill, Props }
import akka.pattern.pipe
import akka.stream.{ ActorMaterializer, UniqueKillSwitch }
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.scaladsl.SourceQueueWithComplete
import cats.data.OptionT
import cats.instances.future._
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowInstanceExecution._
import com.flowtick.sysiphos.execution.FlowTaskExecution.TaskStreamFailure
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class FlowInstanceExecutorActor(flowInstanceId: String, flowDefinitionId: String)(
  clusterContext: ClusterContext,
  flowExecutorActor: ActorRef,
  logger: Logger)(implicit repositoryContext: RepositoryContext, implicit val executionContext: ExecutionContext)
  extends Actor with FlowInstanceExecution with FlowInstanceTaskStream {
  import Logging._

  type QueueType = ((SourceQueueWithComplete[FlowTask], UniqueKillSwitch), NotUsed)

  implicit val actorSystem: ActorSystem = context.system
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val taskExecutorProps = Props(new FlowTaskExecutionActor(self, context.parent, logger))
  var taskQueue: Option[QueueType] = None

  override def postStop(): Unit = {
    log.debug("shutting down task stream")
    taskQueue.foreach {
      case ((_, killSwitch), _) =>
        Try(killSwitch.shutdown()).failed.foreach(error => log.warn(s"unable to shutdown task stream, ${error.getMessage}"))
    }
  }

  def enqueueNextTasks(
    flowDefinition: FlowDefinition,
    taskSelection: FlowTaskSelection,
    currentInstances: Seq[FlowTaskInstanceDetails]): Future[Seq[Either[Throwable, FlowTask]]] = taskQueue.map {
    case ((queue, _), _) =>
      for {
        next <- Future.successful(nextFlowTasks(
          taskSelection,
          flowDefinition,
          currentInstances))

        enqueued <- Future.sequence(next.map { flowTask =>
          queue.offer(flowTask).flatMap[Either[Throwable, FlowTask]] {
            case Enqueued => Future.successful(Right(flowTask))
            case other => Future.successful(Left(new RuntimeException(s"unable to queue $flowTask, $other")))
          }.recoverWith {
            case error => Future.successful(Left(error))
          }
        })
      } yield enqueued
  }.getOrElse(Future.failed(new IllegalStateException("task queue should be created before execution")))

  override def receive: PartialFunction[Any, Unit] = {
    case FlowInstanceExecution.Run(taskSelection) =>
      log.info(s"executing $flowDefinitionId instance $flowInstanceId...")

      clusterContext.flowDefinitionRepository
        .findById(flowDefinitionId)
        .flatMap {
          case Some(flowDefinitionDetails) => Future.successful(FlowDefinition.fromJson(flowDefinitionDetails.source.get))
          case None => Future.failed(new IllegalStateException(s"unable to find flow definition ${flowDefinitionId}"))
        }.map {
          case Right(flowDefinition) =>
            val taskParallelism: Int = flowDefinition.taskParallelism.getOrElse(1)

            if (taskQueue.isEmpty) {
              taskQueue = Some(createTaskStream(clusterContext.flowTaskInstanceRepository, clusterContext.flowInstanceRepository, self, flowExecutorActor)(
                flowInstanceId,
                flowDefinitionId,
                taskParallelism,
                flowDefinition.taskRatePerSecond.getOrElse(1),
                1.seconds,
                logger)(repositoryContext).run)
            }

            (for {
              currentInstances <- clusterContext.flowTaskInstanceRepository.find(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstanceId)))

              enqueueResult <- enqueueNextTasks(flowDefinition, taskSelection, currentInstances).logSuccess(result => s"new in queue: $result")

              executionResult <- if (enqueueResult.exists(_.isRight) || currentInstances.exists(isPending)) {
                Future.successful(WorkPending(flowInstanceId))
              } else if (currentInstances.forall(_.status == FlowTaskInstanceStatus.Done)) {
                Future.successful(Finished(flowInstanceId, flowDefinitionId))
              } else {
                Future.successful(ExecutionFailed(new IllegalStateException("no task candidate found"), flowInstanceId, flowDefinitionId))
              }

            } yield executionResult)
              .logSuccess(executionResult => s"execution result $executionResult")
              .logFailed(s"unable to execute $flowInstanceId").pipeTo(flowExecutorActor)

          case Left(error) => Future.failed(error)
        }

    case FlowInstanceExecution.WorkFailed(error, optionalTaskInstance) => optionalTaskInstance match {
      case Some(flowTaskInstance) =>
        Monitoring.count(
          key = "work-failed",
          tags = Map("definition" -> flowTaskInstance.flowDefinitionId, "task" -> flowTaskInstance.taskId))

        val taskFailedMessage = s"task ${flowTaskInstance.id} failed with ${error.getLocalizedMessage}"

        log.warn(taskFailedMessage)

        logger.appendLine(flowTaskInstance.logId, taskFailedMessage).unsafeRunSync()

        val handledFailure = for {
          _ <- clusterContext.flowTaskInstanceRepository.setEndTime(flowTaskInstance.id, repositoryContext.epochSeconds)
          handled <- handleFailedTask(flowTaskInstance, error)
        } yield handled

        handledFailure.logFailed(s"unable to handle failure in ${flowTaskInstance.id}").pipeTo(flowExecutorActor)
      case None =>
        log.error("error during task execution", error)
    }

    case FlowInstanceExecution.WorkDone(flowTaskInstance, addToContext) =>
      log.info(s"Work is done for task with id ${flowTaskInstance.taskId}.")

      Monitoring.count("work-done", Map(
        "definition" -> flowTaskInstance.flowDefinitionId,
        "task" -> flowTaskInstance.taskId))

      val doneTaskInstance = for {
        _ <- clusterContext.flowInstanceRepository.insertOrUpdateContextValues(flowTaskInstance.flowInstanceId, addToContext)
        _ <- clusterContext.flowTaskInstanceRepository.setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Done, None, None)
        ended <- clusterContext.flowTaskInstanceRepository.setEndTime(flowTaskInstance.id, repositoryContext.epochSeconds)
      } yield ended

      OptionT(doneTaskInstance)
        .getOrElseF(Future.failed(new IllegalStateException("flow task instance not updated")))
        .map(TaskCompleted)
        .pipeTo(flowExecutorActor)
        .logFailed(s"unable to complete task ${flowTaskInstance.id}")
        .foreach(_ => Monitoring.count("task-completed", Map(
          "definition" -> flowTaskInstance.flowDefinitionId,
          "task" -> flowTaskInstance.taskId)))

    case TaskStreamFailure(error) =>
      log.error("error in task stream", error)
      sender() ! PoisonPill
      flowExecutorActor ! FlowInstanceExecution.ExecutionFailed(error, flowInstanceId, flowDefinitionId)

    case other => log.warn(s"unhandled message: $other")
  }

  private def handleFailedTask(flowTaskInstance: FlowTaskInstance, error: Throwable): Future[FlowInstanceMessage] =
    if (flowTaskInstance.retries == 0) {
      val message = s"retries exceeded for $flowTaskInstance"
      log.error(message, error)
      clusterContext.flowTaskInstanceRepository
        .setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Failed, None, None)
        .map(_ => FlowInstanceExecution.ExecutionFailed(new RuntimeException(message, error), flowTaskInstance.flowInstanceId, flowTaskInstance.flowDefinitionId))
    } else {
      val dueDate = repositoryContext.epochSeconds + flowTaskInstance.retryDelay
      log.info(s"scheduling retry for ${flowTaskInstance.id} for $dueDate")

      for {
        _ <- clusterContext.flowTaskInstanceRepository.setStatus(
          flowTaskInstance.id,
          FlowTaskInstanceStatus.Retry,
          Some(flowTaskInstance.retries - 1),
          Some(dueDate))
      } yield RetryScheduled(flowTaskInstance)
    }
}

