package com.flowtick.sysiphos.execution

import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, ActorRef, Cancellable, PoisonPill }
import akka.pattern.pipe
import cats.effect.IO
import cats.syntax.all._
import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.core.{ DefaultRepositoryContext, RepositoryContext }
import com.flowtick.sysiphos.execution.FlowExecutorActor.{ CreatedOrUpdatedDefinition, ImportDefinition, NewInstance, RequestInstance }
import com.flowtick.sysiphos.flow.{ FlowInstance, _ }
import com.flowtick.sysiphos.scheduler.{ FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

object FlowExecutorActor {
  case class Init(workerRouterPool: ActorRef)
  case object Tick
  case class RequestInstance(flowDefinitionId: String, context: Seq[FlowInstanceContextValue])
  case class ImportDefinition(flowDefinition: FlowDefinition)
  case class CreatedOrUpdatedDefinition(result: Either[Throwable, FlowDefinitionDetails])
  case class NewInstance(result: Either[Throwable, FlowInstanceContext])
  case class RunInstanceExecutors(instances: Seq[FlowInstance])
}

class FlowExecutorActor(
  val flowScheduleRepository: FlowScheduleRepository,
  val flowInstanceRepository: FlowInstanceRepository,
  val flowDefinitionRepository: FlowDefinitionRepository,
  val flowTaskInstanceRepository: FlowTaskInstanceRepository,
  val flowScheduleStateStore: FlowScheduleStateStore,
  val flowScheduler: FlowScheduler)(implicit val executionContext: ExecutionContext) extends Actor with FlowExecution with Logging {
  import Logging._

  def initialDelay = FiniteDuration(Configuration.propOrEnv("scheduler.delay.ms").map(_.toInt).getOrElse(10000), TimeUnit.MILLISECONDS)
  def tickInterval = FiniteDuration(Configuration.propOrEnv("scheduler.interval.ms").map(_.toInt).getOrElse(10000), TimeUnit.MILLISECONDS)

  var workerPool: Option[ActorRef] = None

  override implicit val repositoryContext: RepositoryContext = new DefaultRepositoryContext("executor")

  override def receive: PartialFunction[Any, Unit] = {
    case FlowExecutorActor.Init(pool) =>
      workerPool = Some(pool)
      init
    case FlowExecutorActor.Tick =>
      log.debug("tick.")

      (for {
        scheduledInstances <- executeScheduled.handleErrorWith(error => IO(log.error("unable to schedule instances", error)) *> IO(List.empty))
        retriedInstances <- executeRetries.handleErrorWith(error => IO(log.error("unable to retry instances", error)) *> IO(List.empty))
        _ <- if (retriedInstances.nonEmpty) IO(log.info(s"retry instances: $retriedInstances")) else IO.unit
        _ <- if (scheduledInstances.nonEmpty) IO(log.info(s"scheduled instances: $scheduledInstances")) else IO.unit
      } yield ()).unsafeToFuture()

    case RequestInstance(flowDefinitionId, instanceContext) =>
      flowDefinitionRepository.findById(flowDefinitionId).flatMap {
        case Some(flowDefinition) =>
          flowInstanceRepository
            .createFlowInstance(flowDefinition.id, instanceContext, initialStatus = FlowInstanceStatus.Triggered)
            .map(instance => NewInstance(Right(instance)))
        case None =>
          Future.successful(FlowExecutorActor.NewInstance(Left(new IllegalArgumentException(s"unable to find flow id $flowDefinitionId"))))
      }.pipeTo(sender())

    case ImportDefinition(flowDefinition) =>
      flowDefinitionRepository
        .createOrUpdateFlowDefinition(flowDefinition)
        .logFailed(s"unable to create or update definition ${flowDefinition.id}")
        .map(definition => CreatedOrUpdatedDefinition(Right(definition)))
        .recoverWith {
          case error => Future.successful(Left(error))
        }.pipeTo(sender())

    case FlowInstanceExecution.Finished(flowInstanceId, flowDefinitionId) =>
      log.info(s"finished $flowInstanceId")
      Monitoring.count("instance-finished", Map("definition" -> flowDefinitionId))

      sender() ! PoisonPill

      for {
        _ <- flowInstanceRepository.setStatus(flowInstanceId, FlowInstanceStatus.Done)
        _ <- flowInstanceRepository.setEndTime(flowInstanceId, repositoryContext.epochSeconds)
      } yield ()

    case FlowInstanceExecution.ExecutionFailed(reason, flowInstanceId, flowDefinitionId) =>
      Monitoring.count("instance-failed", Map("definition" -> flowDefinitionId))

      sender() ! PoisonPill

      for {
        _ <- flowInstanceRepository.update(FlowInstanceQuery(instanceIds = Some(Seq(flowInstanceId))), FlowInstanceStatus.Failed, Some(reason))
        _ <- flowInstanceRepository.setEndTime(flowInstanceId, repositoryContext.epochSeconds)
      } yield ()

    case FlowInstanceExecution.TaskCompleted(taskInstance) =>
      log.info(s"task completed: ${taskInstance.id}")
      sender() ! FlowInstanceExecution.Run(PendingTasks)

    case FlowInstanceExecution.WorkPending(instanceId) =>
      log.info(s"work pending: $instanceId")

    case FlowInstanceExecution.RetryScheduled(taskInstance) =>
      log.info(s"retry scheduled: $taskInstance")
      Monitoring.count("retry-scheduled", Map("task" -> taskInstance.taskId))
      sender() ! FlowInstanceExecution.Run(PendingTasks)

    case execute: FlowInstanceExecution.Execute => workerPool.foreach(_ ! execute)
  }

  def init: Future[Cancellable] = {
    log.info("restarting abandoned running instances...")

    val runningInstances = FlowInstanceQuery(flowDefinitionId = None, status = Some(Seq(FlowInstanceStatus.Running)))
    val runningTasks = FlowTaskInstanceQuery(status = Some(Seq(FlowTaskInstanceStatus.Running)))

    for {
      _ <- flowInstanceRepository.update(runningInstances, FlowInstanceStatus.Triggered, None)
      _ <- flowTaskInstanceRepository.update(runningTasks, FlowTaskInstanceStatus.Retry, None, None)
    } yield {
      log.info("initializing scheduler...")
      context.system.scheduler.schedule(initialDelay, tickInterval, self, FlowExecutorActor.Tick)(context.system.dispatcher)
    }
  }

  override def executeRunning(
    running: FlowInstanceDetails,
    selectedTaskId: Option[String]): Future[Any] = workerPool.map { pool =>
    Future
      .successful(FlowInstanceExecution.Execute(running.id, running.flowDefinitionId, selectedTaskId.map(TaskId).getOrElse(PendingTasks)))
      .pipeTo(pool)
  }.getOrElse(Future.failed(new IllegalStateException(s"asked to execute $running, but there is no worker pool")))

}

