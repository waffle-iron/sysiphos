package com.flowtick.sysiphos.execution

import akka.NotUsed
import akka.actor.{ Actor, ActorRef, ActorSystem, PoisonPill, Props }
import akka.pattern.pipe
import akka.stream.{ ActorMaterializer, UniqueKillSwitch }
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.scaladsl.SourceQueueWithComplete
import cats.data.OptionT
import cats.effect.{ ContextShift, IO, Timer }
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
  implicit val timer: Timer[IO] = cats.effect.IO.timer(executionContext)
  implicit val cs: ContextShift[IO] = cats.effect.IO.contextShift(executionContext)

  def taskDefinition = clusterContext.flowDefinitionRepository
    .findById(flowDefinitionId)
    .flatMap {
      case Some(flowDefinitionDetails) => Future.successful(
        FlowDefinition.fromJson(flowDefinitionDetails.source.get))
      case None => Future.failed(new IllegalStateException(s"unable to find flow definition ${flowDefinitionId}"))
    }

  def onFailureTask: Future[Option[FlowTask]] = taskDefinition.flatMap {
    case Right(flowDefinition) => Future.successful(flowDefinition.onFailure)
    case Left(error) => Future.failed(error)
  }

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

  def taskFailed(flowTaskInstance: FlowTaskInstance, error: Throwable): Future[FlowInstanceMessage] = {
    val handledFailure = for {
      _ <- clusterContext.flowTaskInstanceRepository.setEndTime(flowTaskInstance.id, repositoryContext.epochSeconds)
      handled <- handleFailedTask(flowTaskInstance, error)
    } yield handled

    handledFailure.logFailed(s"unable to handle failure in ${flowTaskInstance.id}").pipeTo(flowExecutorActor)
  }

  override def receive: PartialFunction[Any, Unit] = {
    case FlowInstanceExecution.Run(taskSelection) =>
      log.info(s"executing $flowDefinitionId instance $flowInstanceId...")
      taskDefinition.map {
        case Right(flowDefinition) =>
          val taskParallelism: Int = flowDefinition.taskParallelism.getOrElse(1)

          if (taskQueue.isEmpty) {
            taskQueue = Some(createTaskStream(clusterContext.flowTaskInstanceRepository, clusterContext.flowInstanceRepository, self, flowExecutorActor)(
              flowInstanceId,
              flowDefinitionId,
              taskParallelism,
              flowDefinition.taskRatePerSecond.getOrElse(1),
              1.seconds,
              logger)(repositoryContext, timer, cs).run)
          }

          (for {
            currentInstances <- clusterContext.flowTaskInstanceRepository.find(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstanceId)))

            enqueueResult <- enqueueNextTasks(flowDefinition, taskSelection, currentInstances).logSuccess(result => s"new in queue: $result")

            executionResult <- if (enqueueResult.exists(_.isRight) || currentInstances.exists(isPending)) {
              Future.successful(WorkPending(flowInstanceId))
            } else if (currentInstances.forall(_.status == FlowTaskInstanceStatus.Done)) {
              Future.successful(Finished(flowInstanceId, flowDefinitionId))
            } else {
              Future.successful(ExecutionFailed(Some(new IllegalStateException("no task candidate found")), flowInstanceId, flowDefinitionId))
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

        flowTaskInstance.onFailureTaskId.map { failureTask =>
          logger.appendLine(flowTaskInstance.logId, s"Running onFailure task for failed task ${flowTaskInstance.id}").unsafeRunSync()
          self ! Run(TaskId(failureTask))
        }.getOrElse {
          taskFailed(flowTaskInstance, error)
        }

      case None =>
        log.error("error during task execution", error)
    }

    case FlowInstanceExecution.WorkDone(flowTaskInstance, addToContext) =>
      log.info(s"Work is done for task with id ${flowTaskInstance.taskId}.")

      /**
       * Get parent of onFailure Task. If it's not an onFailure task, it will be None
       * @param id task id
       * @return None if it's not an onFailureTask, and the parent Task if it is.
       */
      def getParentOfFailureTask(id: String): Future[Option[FlowTask]] = {
        taskDefinition.map {
          case Right(definition) =>
            definition.tasks
              .flatMap(parentTask => parentTask.onFailure
                .filter(_.id == id)
                .map(_ => parentTask))
              .headOption
        }
      }

      getParentOfFailureTask(flowTaskInstance.taskId).map {
        case Some(parent) =>
          //set parent failed
          clusterContext.flowTaskInstanceRepository
            .findOne(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstanceId), taskId = Some(parent.id))).map {
              case Some(flowTaskInstanceDetails) =>
                taskFailed(flowTaskInstanceDetails, new IllegalArgumentException(s"Task id ${parent.id} failed and on failure task ${flowTaskInstance.taskId} was executed"))
            }

        case None =>
          Monitoring.count("work-done", Map(
            "definition" -> flowTaskInstance.flowDefinitionId,
            "task" -> flowTaskInstance.taskId))
      }

      val doneTaskInstance = for {
        _ <- clusterContext.flowInstanceRepository.insertOrUpdateContextValues(flowTaskInstance.flowInstanceId, addToContext)
        _ <- clusterContext.flowTaskInstanceRepository.setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Done, None, None)
        ended <- clusterContext.flowTaskInstanceRepository.setEndTime(flowTaskInstance.id, repositoryContext.epochSeconds)
      } yield ended

      val maybeOnFailureTaskId = onFailureTask.map { failureTask => failureTask.exists(_.id == flowTaskInstance.taskId) }

      val messageToFlowExecutor: FlowTaskInstanceDetails => Future[FlowInstanceMessage] = taskInstanceDetails => maybeOnFailureTaskId.map {
        case true => FlowInstanceExecution.ExecutionFailed(
          reason = None,
          flowInstanceId = taskInstanceDetails.flowInstanceId,
          flowDefinitionId = taskInstanceDetails.flowDefinitionId,
          onFailureTaskId = None)
        case false => TaskCompleted(taskInstanceDetails)
      }

      OptionT(doneTaskInstance)
        .getOrElseF(Future.failed(new IllegalStateException("flow task instance not updated")))
        .flatMap(messageToFlowExecutor)
        .pipeTo(flowExecutorActor)
        .logFailed(s"unable to complete task ${flowTaskInstance.id}")
        .foreach(_ => Monitoring.count("task-completed", Map(
          "definition" -> flowTaskInstance.flowDefinitionId,
          "task" -> flowTaskInstance.taskId)))

    case TaskStreamFailure(error) =>
      log.error("error in task stream", error)
      sender() ! PoisonPill
      flowExecutorActor ! FlowInstanceExecution.ExecutionFailed(Option(error), flowInstanceId, flowDefinitionId)

    case other => log.warn(s"unhandled message: $other")
  }

  private def handleFailedTask(flowTaskInstance: FlowTaskInstance, error: Throwable): Future[FlowInstanceMessage] =
    if (flowTaskInstance.retries == 0) {
      val message = s"retries exceeded for $flowTaskInstance"
      log.error(message, error)

      for {
        _ <- clusterContext.flowTaskInstanceRepository.setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Failed, None, None)
        maybeFailureTask <- onFailureTask
        failureMessage <- if (maybeFailureTask.exists(_.id != flowTaskInstance.taskId))
          Future.successful(FlowInstanceExecution.ExecutionFailed(
            Some(new RuntimeException(message, error)),
            flowTaskInstance.flowInstanceId,
            flowTaskInstance.flowDefinitionId,
            onFailureTaskId = maybeFailureTask.map(_.id)))
        else Future.successful(FlowInstanceExecution.ExecutionFailed(
          Some(new RuntimeException(message, error)),
          flowTaskInstance.flowInstanceId,
          flowTaskInstance.flowDefinitionId,
          None))
      } yield failureMessage
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

