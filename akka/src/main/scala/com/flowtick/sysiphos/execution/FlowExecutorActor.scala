package com.flowtick.sysiphos.execution

import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, ActorRef, Cancellable, PoisonPill }
import akka.pattern.pipe
import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.core.{ DefaultRepositoryContext, RepositoryContext }
import com.flowtick.sysiphos.execution.FlowExecutorActor.{ CreatedOrUpdatedDefinition, ImportDefinition, NewInstance, RequestInstance }
import com.flowtick.sysiphos.flow.{ FlowInstance, _ }
import com.flowtick.sysiphos.scheduler.{ FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }
import kamon.Kamon

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

object FlowExecutorActor {
  case class Init(workerRouterPool: ActorRef)
  case object Tick
  case class RequestInstance(flowDefinitionId: String, context: Seq[FlowInstanceContextValue])
  case class ImportDefinition(flowDefinition: FlowDefinition)
  case class CreatedOrUpdatedDefinition(result: Either[Throwable, FlowDefinitionDetails])
  case class NewInstance(result: Either[Throwable, FlowInstanceDetails])
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
      executeScheduled()
      executeRetries()

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
      Kamon.counter("instance-finished").refine("definition", flowDefinitionId).increment()

      sender() ! PoisonPill

      for {
        _ <- flowInstanceRepository.setStatus(flowInstanceId, FlowInstanceStatus.Done)
        _ <- flowInstanceRepository.setEndTime(flowInstanceId, repositoryContext.epochSeconds)
      } yield ()

    case FlowInstanceExecution.ExecutionFailed(flowInstanceId, flowDefinitionId) =>
      Kamon.counter("instance-failed").refine("definition", flowDefinitionId).increment()

      sender() ! PoisonPill
      for {
        _ <- flowInstanceRepository.setStatus(flowInstanceId, FlowInstanceStatus.Failed)
        _ <- flowInstanceRepository.setEndTime(flowInstanceId, repositoryContext.epochSeconds)
      } yield ()

    case FlowInstanceExecution.TaskCompleted(taskInstance) =>
      log.info(s"task completed: ${taskInstance.id}")
      executePending(taskInstance)

    case FlowInstanceExecution.WorkPending(instanceId) =>
      log.info(s"work pending: $instanceId")

    case FlowInstanceExecution.RetryScheduled(taskInstance) =>
      log.info(s"retry scheduled: $taskInstance")
      Kamon.counter("retry-scheduled").refine("task", taskInstance.taskId).increment()
      executePending(taskInstance)
  }

  def executePending(taskInstance: FlowTaskInstance): Future[FlowInstanceExecution.Execute] = {
    flowInstanceRepository
      .findById(taskInstance.flowInstanceId)
      .flatMap {
        case Some(instance) => Future.successful(FlowInstanceExecution.Execute(instance, PendingTasks))
        case None => Future.failed(new IllegalStateException("unable to find task instance"))
      }.pipeTo(sender())
  }

  def init: Future[Cancellable] = {
    log.info("restarting abandoned running instances...")

    val runningInstances = FlowInstanceQuery(flowDefinitionId = None, status = Some(Seq(FlowInstanceStatus.Running)))
    val runningTasks = FlowTaskInstanceQuery(status = Some(Seq(FlowTaskInstanceStatus.Running)))

    for {
      _ <- flowInstanceRepository.update(runningInstances, FlowInstanceStatus.Triggered)
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
      .successful(FlowInstanceExecution.Execute(running, selectedTaskId.map(TaskId).getOrElse(PendingTasks)))
      .pipeTo(pool)(sender())
  }.getOrElse(Future.failed(new IllegalStateException(s"asked to execute $running, but there is no worker pool")))

}

