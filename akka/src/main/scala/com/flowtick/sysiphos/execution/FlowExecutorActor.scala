package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneId }
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, Cancellable, Props }
import akka.pattern.pipe
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowInstance, _ }
import com.flowtick.sysiphos.scheduler.{ FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

object FlowExecutorActor {
  case class Init()
  case class Tick()
  case class RunInstanceExecutors(instances: Seq[FlowInstance])
  case class DueFlowDefinitions(flows: Seq[FlowDefinition])
}

class FlowExecutorActor(
  val flowScheduleRepository: FlowScheduleRepository,
  val flowInstanceRepository: FlowInstanceRepository,
  val flowDefinitionRepository: FlowDefinitionRepository,
  val flowTaskInstanceRepository: FlowTaskInstanceRepository,
  val flowScheduleStateStore: FlowScheduleStateStore,
  val flowScheduler: FlowScheduler)(implicit val executionContext: ExecutionContext) extends Actor with FlowExecution with Logging {

  val initialDelay = FiniteDuration(10000, TimeUnit.MILLISECONDS)
  val tickInterval = FiniteDuration(10000, TimeUnit.MILLISECONDS)

  def now: Long = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond

  override implicit val repositoryContext: RepositoryContext = new RepositoryContext {
    override def currentUser: String = "undefined"
  }

  def flowInstanceActorProps(flowDefinition: FlowDefinition, flowInstance: FlowInstance) = Props(
    new FlowInstanceExecutorActor(
      flowDefinition,
      flowInstance,
      flowInstanceRepository,
      flowTaskInstanceRepository)(repositoryContext))

  def executeInstance(instance: FlowInstance): Future[FlowInstanceExecution.FlowInstanceMessage] = {
    val maybeFlowDefinition = flowDefinitionRepository.findById(instance.flowDefinitionId).map {
      case Some(details) =>
        details.source.flatMap(FlowDefinition.fromJson(_).right.toOption)
      case None => None
    }

    val flowInstanceInit: Future[FlowInstanceExecution.FlowInstanceMessage] = maybeFlowDefinition.flatMap {
      case Some(definition) =>
        flowInstanceRepository.setStatus(instance.id, FlowInstanceStatus.Running).flatMap {
          case Some(running) =>
            Future
              .successful(FlowInstanceExecution.Execute)
              .pipeTo(context.actorOf(flowInstanceActorProps(definition, running)))(sender())
          case None => Future.failed(new IllegalStateException("unable to update flow instance"))
        }
      case None => Future.failed(new IllegalStateException("unable to find flow definition"))
    }

    flowInstanceInit
  }

  override def receive: PartialFunction[Any, Unit] = {
    case _: FlowExecutorActor.Init => init
    case _: FlowExecutorActor.Tick =>
      val taskInstancesFuture: Future[Seq[FlowInstance]] = for {
        manuallyTriggered <- manuallyTriggeredInstances
        newTaskInstances <- dueScheduledFlowInstances(now)
        retryTaskInstances <- dueTaskRetries(now)
      } yield newTaskInstances ++ retryTaskInstances ++ manuallyTriggered

      taskInstancesFuture.foreach { instances => instances.map(executeInstance) }

    case FlowInstanceExecution.Finished(flowInstance) =>
      flowInstanceRepository.setStatus(flowInstance.id, FlowInstanceStatus.Done)
    case FlowInstanceExecution.ExecutionFailed(flowTaskInstance) =>
      flowInstanceRepository.setStatus(flowTaskInstance.flowInstanceId, FlowInstanceStatus.Failed)
    case FlowInstanceExecution.Retry(flowTaskInstance) =>
      val dueDate = now + flowTaskInstance.retryDelay.getOrElse(10L)
      log.info(s"scheduling retry for ${flowTaskInstance.id} for $dueDate")
      flowTaskInstanceRepository.setNextDueDate(flowTaskInstance.id, Some(dueDate))
  }

  def init: Cancellable = {
    log.info("initializing scheduler...")

    context.system.scheduler.schedule(initialDelay, tickInterval, self, FlowExecutorActor.Tick())(context.system.dispatcher)
  }
}

