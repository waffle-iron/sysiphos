package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneId }
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, Cancellable, PoisonPill, Props }
import akka.pattern.pipe
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowExecutorActor.{ NewInstance, RequestInstance }
import com.flowtick.sysiphos.flow.{ FlowInstance, _ }
import Logging._
import com.flowtick.sysiphos.scheduler.{ FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

object FlowExecutorActor {
  case class Init()
  case class Tick()
  case class RequestInstance(flowDefinitionId: String, context: Seq[FlowInstanceContextValue])
  case class NewInstance(result: Either[Throwable, FlowInstanceDetails])
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

  def executeInstance(instance: FlowInstance, selectedTaskId: Option[String]): Future[FlowInstanceExecution.FlowInstanceMessage] = {
    val maybeFlowDefinition = flowDefinitionRepository.findById(instance.flowDefinitionId).map {
      case Some(details) =>
        details.source.flatMap(FlowDefinition.fromJson(_).right.toOption)
      case None => None
    }

    val flowInstanceInit: Future[FlowInstanceExecution.FlowInstanceMessage] = maybeFlowDefinition.flatMap {
      case Some(definition) =>
        val runningInstance = for {
          _ <- if (instance.startTime.isEmpty)
            flowInstanceRepository.setStartTime(instance.id, repositoryContext.epochSeconds)
          else
            Future.successful(())
          running <- flowInstanceRepository.setStatus(instance.id, FlowInstanceStatus.Running)
        } yield running

        runningInstance.flatMap {
          case Some(running) =>
            val instanceActor = context.child(running.id).getOrElse(
              context.actorOf(flowInstanceActorProps(definition, running), running.id))

            Future
              .successful(FlowInstanceExecution.Execute(selectedTaskId))
              .pipeTo(instanceActor)(sender())
          case None => Future.failed(new IllegalStateException("unable to update flow instance"))
        }
      case None => Future.failed(new IllegalStateException("unable to find flow definition"))
    }

    flowInstanceInit
  }

  def executeScheduled(): Unit = {
    val taskInstancesFuture: Future[Seq[FlowInstance]] = for {
      manuallyTriggered <- manuallyTriggeredInstances.logFailed("unable to get manually triggered instances")
      newTaskInstances <- dueScheduledFlowInstances(now).logFailed("unable to get scheduled flow instance")
    } yield newTaskInstances ++ manuallyTriggered

    taskInstancesFuture.foreach { instances => instances.foreach(executeInstance(_, None)) }
  }

  def executeRetries(): Unit = {
    dueTaskRetries(now).logFailed("unable to get due tasks").foreach { dueTasks =>
      dueTasks.foreach {
        case (Some(instance), taskId) => executeInstance(instance, Some(taskId))
        case _ =>
      }
    }
  }

  override def receive: PartialFunction[Any, Unit] = {
    case _: FlowExecutorActor.Init => init
    case _: FlowExecutorActor.Tick =>
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

    case FlowInstanceExecution.Finished(flowInstance) =>
      log.info(s"finished $flowInstance")
      for {
        _ <- flowInstanceRepository.setStatus(flowInstance.id, FlowInstanceStatus.Done)
        _ <- flowInstanceRepository.setEndTime(flowInstance.id, repositoryContext.epochSeconds)
      } yield ()

    case FlowInstanceExecution.WorkTriggered(tasks) =>
      log.info(s"new work started: $tasks")

    case FlowInstanceExecution.ExecutionFailed(flowInstance) =>
      context.sender() ! PoisonPill
      for {
        _ <- flowInstanceRepository.setStatus(flowInstance.id, FlowInstanceStatus.Failed)
        _ <- flowInstanceRepository.setEndTime(flowInstance.id, repositoryContext.epochSeconds)
      } yield ()

    case FlowInstanceExecution.RetryScheduled(instance) =>
      log.info(s"retry scheduled: $instance")
  }

  def init: Cancellable = {
    log.info("initializing scheduler...")

    context.system.scheduler.schedule(initialDelay, tickInterval, self, FlowExecutorActor.Tick())(context.system.dispatcher)
  }
}

