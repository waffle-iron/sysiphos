package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneId }
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, Cancellable, PoisonPill, Props }
import akka.pattern.pipe
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowExecutorActor.{ NewInstance, RequestInstance }
import com.flowtick.sysiphos.flow.{ FlowInstance, _ }
import Logging._
import cats.data.{ EitherT, OptionT }
import com.flowtick.sysiphos.scheduler.{ FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import cats.instances.future._

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

  def executeInstances(flowDefinitionFuture: Future[Option[FlowDefinitionDetails]], instancesToRun: Seq[FlowInstance]): Future[Seq[FlowInstanceExecution.FlowInstanceMessage]] = {
    def runningInstances(flowDefinitionId: String): Future[Seq[InstanceCount]] =
      flowInstanceRepository.counts(Option(Seq(flowDefinitionId)), Option(Seq(FlowInstanceStatus.Running)), None)

    val withParallelism: OptionT[Future, Seq[FlowInstance]] = for {
      flowDefinitionDetails <- OptionT[Future, FlowDefinitionDetails](flowDefinitionFuture)
      counts <- OptionT.liftF(runningInstances(flowDefinitionDetails.id))
    } yield {
      val definitionInstancesRunning: Option[Int] = counts
        .groupBy(_.flowDefinitionId)
        .get(FlowInstanceStatus.Running.toString)
        .flatMap(_.headOption.map(_.count))

      val flowDefinition: Either[Exception, FlowDefinition] = flowDefinitionDetails.source
        .map(FlowDefinition.fromJson)
        .getOrElse(Left(new IllegalArgumentException(s"no source for ${flowDefinitionDetails.id}")))

      flowDefinition.left.foreach(log.error("unable to get flow definition", _))

      val chosenInstances = for {
        definition <- flowDefinition.toOption
        parallelism <- Option(definition.parallelism.getOrElse(Integer.MAX_VALUE))
        runningInstances <- definitionInstancesRunning
        newInstances <- Option(parallelism - runningInstances).filter(_ > 0)
      } yield instancesToRun.take(newInstances)

      chosenInstances.getOrElse(Seq.empty)
    }

    withParallelism
      .map(instances => Future.sequence(instances.map(executeInstance(_, None))))
      .value
      .flatMap(_.getOrElse(Future.successful(Seq.empty)))
  }

  def executeInstance(instance: FlowInstance, selectedTaskId: Option[String]): Future[FlowInstanceExecution.FlowInstanceMessage] = {

    val maybeFlowDefinition: EitherT[Future, String, FlowDefinition] = for {
      details <- EitherT.fromOptionF(flowDefinitionRepository.findById(instance.flowDefinitionId), s"unable to find definition for id ${instance.flowDefinitionId}")
      source <- EitherT.fromOption(details.source, s"source flow definition missing for id ${instance.flowDefinitionId}")
      parsedFlowDefinition <- EitherT.fromEither(FlowDefinition.fromJson(source)).leftMap(e => e.getLocalizedMessage)
    } yield parsedFlowDefinition

    val flowInstanceInit = maybeFlowDefinition.flatMap { definition =>
      val runningInstance = for {
        _ <- if (instance.startTime.isEmpty)
          flowInstanceRepository.setStartTime(instance.id, repositoryContext.epochSeconds)
        else
          Future.successful(())
        running <- flowInstanceRepository.setStatus(instance.id, FlowInstanceStatus.Running)
      } yield running

      EitherT.fromOptionF(runningInstance, "unable to update flow instance").map { running =>
        val instanceActor = context.child(running.id).getOrElse(
          context.actorOf(flowInstanceActorProps(definition, running), running.id))

        val executeMessage = FlowInstanceExecution.Execute(selectedTaskId)

        Future
          .successful(executeMessage)
          .pipeTo(instanceActor)(sender())

        executeMessage
      }
    }

    flowInstanceInit.value.flatMap {
      case Left(message) => Future.failed(new IllegalStateException(message))
      case Right(value) => Future.successful(value)
    }
  }

  def executeScheduled(): Unit = {
    val taskInstancesFuture: Future[Seq[FlowInstance]] = for {
      manuallyTriggered <- manuallyTriggeredInstances.logFailed("unable to get manually triggered instances")
      newTaskInstances <- dueScheduledFlowInstances(now).logFailed("unable to get scheduled flow instance")
    } yield newTaskInstances ++ manuallyTriggered

    taskInstancesFuture.foreach { instances =>
      instances
        .groupBy(_.flowDefinitionId)
        .foreach {
          case (flowDefinitionId, triggeredInstances) =>
            executeInstances(flowDefinitionRepository.findById(flowDefinitionId), triggeredInstances)
        }
    }
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

