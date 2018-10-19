package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneId }
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, Cancellable, PoisonPill, Props }
import akka.pattern.pipe
import cats.data.{ EitherT, OptionT }
import cats.instances.future._
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowExecutorActor.{ NewInstance, RequestInstance }
import com.flowtick.sysiphos.execution.Logging._
import com.flowtick.sysiphos.flow.{ FlowInstance, _ }
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

  def executeInstances(
    flowDefinition: FlowDefinition,
    instancesToRun: Seq[FlowInstance]): Future[Seq[FlowInstanceExecution.FlowInstanceMessage]] = {
    def runningInstances(flowDefinitionId: String): Future[Seq[InstanceCount]] =
      flowInstanceRepository.counts(Option(Seq(flowDefinitionId)), Option(Seq(FlowInstanceStatus.Running)), None)

    val withParallelism = runningInstances(flowDefinition.id).map { counts =>
      val definitionInstancesRunning: Option[Int] = counts
        .groupBy(_.flowDefinitionId)
        .get(FlowInstanceStatus.Running.toString)
        .flatMap(_.headOption.map(_.count))

      val chosenInstances = for {
        runningInstances <- definitionInstancesRunning
        parallelism <- Option(flowDefinition.parallelism.getOrElse(Integer.MAX_VALUE))
        newInstances <- Option(parallelism - runningInstances).filter(_ > 0)
      } yield instancesToRun.take(newInstances)

      chosenInstances.getOrElse(Seq.empty)
    }

    withParallelism.flatMap(instances => Future.sequence(instances.map(executeInstance(_, None))))
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

    for {
      instances <- taskInstancesFuture
      newInstances <- Future.successful(instances.groupBy(_.flowDefinitionId).toSeq.map {
        case (flowDefinitionId, triggeredInstances) =>
          val flowDefinitionFuture: Future[Option[FlowDefinitionDetails]] = flowDefinitionRepository.findById(flowDefinitionId)

          (for {
            flowDefinitionOrError: Either[Exception, FlowDefinition] <- OptionT(flowDefinitionFuture)
              .getOrElseF(Future.failed(new IllegalStateException("flow definition not found")))
              .map(FlowDefinition.apply)
            flowDefinition <- flowDefinitionOrError match {
              case Right(flowDefinition) => Future.successful(flowDefinition)
              case Left(error) => Future.failed(new IllegalStateException("unable to parse flow definition", error))
            }
            latestTriggered <- latestOnly(flowDefinition, triggeredInstances)
            running <- executeInstances(flowDefinition, latestTriggered)
          } yield running): Future[Seq[FlowInstanceExecution.FlowInstanceMessage]]
      })
    } yield newInstances
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

