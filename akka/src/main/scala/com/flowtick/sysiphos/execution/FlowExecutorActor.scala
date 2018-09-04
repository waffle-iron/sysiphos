package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneOffset }
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, Cancellable, Props }
import akka.pattern.pipe
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowInstance, _ }
import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }

import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object FlowExecutorActor {
  case class Init()
  case class Tick()
  case class RunInstanceExecutors(instances: Seq[FlowInstance])
  case class DueFlowDefinitions(flows: Seq[FlowDefinition])
}

trait FlowExecution extends Logging {
  val flowScheduleRepository: FlowScheduleRepository
  val flowInstanceRepository: FlowInstanceRepository
  val flowScheduleStateStore: FlowScheduleStateStore
  val flowScheduler: FlowScheduler
  implicit val repositoryContext: RepositoryContext

  def createInstance(flowSchedule: FlowSchedule): Future[FlowInstance] = {
    log.debug(s"creating instance for $flowSchedule.")
    flowInstanceRepository.createFlowInstance(flowSchedule.flowDefinitionId, Map.empty)
  }

  def dueTaskInstances(now: Long): Future[Seq[FlowInstance]] = {
    log.debug("tick.")
    val futureEnabledSchedules: Future[Seq[FlowSchedule]] = flowScheduleRepository
      .getFlowSchedules(onlyEnabled = true, None)

    futureEnabledSchedules.flatMap { schedules =>
      log.debug(s"checking schedules: $schedules.")
      Future.sequence {
        schedules.map { s =>
          val maybeFlowInstance = createInstanceIfDue(s, now)

          // schedule next occurrence
          maybeFlowInstance.foreach { _ =>
            flowScheduler
              .nextOccurrence(s, now)
              .map(next => flowScheduleStateStore.setDueDate(s.id, next))
          }

          maybeFlowInstance
        }
      }
    }.map(_.flatten)
  }

  def createInstanceIfDue(schedule: FlowSchedule, now: Long): Future[Option[FlowInstance]] = {
    log.debug(s"checking if $schedule is due.")
    schedule.nextDueDate match {
      case Some(timestamp) if timestamp <= now =>
        createInstance(schedule).map(Option(_))
      case None if schedule.enabled.contains(true) && schedule.expression.isDefined =>
        createInstance(schedule).map(Option(_))
      case _ =>
        log.debug(s"not due: $schedule")
        Future.successful(None)
    }
  }

}

class FlowExecutorActor(
  val flowScheduleRepository: FlowScheduleRepository,
  val flowInstanceRepository: FlowInstanceRepository,
  val flowDefinitionRepository: FlowDefinitionRepository,
  val flowTaskInstanceRepository: FlowTaskInstanceRepository[FlowTaskInstance],
  val flowScheduleStateStore: FlowScheduleStateStore,
  val flowScheduler: FlowScheduler) extends Actor with FlowExecution with Logging {

  val initialDelay = FiniteDuration(10000, TimeUnit.MILLISECONDS)
  val tickInterval = FiniteDuration(10000, TimeUnit.MILLISECONDS)

  def now: LocalDateTime = LocalDateTime.now()

  def zoneOffset: ZoneOffset = ZoneOffset.UTC

  override implicit val repositoryContext: RepositoryContext = new RepositoryContext {
    override def currentUser: String = "undefined"
  }

  override def receive: PartialFunction[Any, Unit] = {
    case _: FlowExecutorActor.Init => init
    case _: FlowExecutorActor.Tick =>
      dueTaskInstances(now.toEpochSecond(zoneOffset)).map { FlowExecutorActor.RunInstanceExecutors }.pipeTo(self)(sender())
    case FlowExecutorActor.RunInstanceExecutors(instances) => instances.foreach { instance =>
      val flowInstanceActorProps = Props(
        new FlowInstanceExecutorActor(
          instance,
          flowInstanceRepository,
          flowTaskInstanceRepository)(repositoryContext))

      val maybeFlowDefinition = flowDefinitionRepository.findById(instance.flowDefinitionId).map {
        case Some(details) =>
          details.source.flatMap(FlowDefinition.fromJson(_).right.toOption)
        case None => None
      }

      val flowInstanceInit: Future[FlowInstanceExecution.Init] = maybeFlowDefinition.flatMap {
        case Some(t) => Future.successful(FlowInstanceExecution.Init(t))
        case None =>
          Future.failed(new RuntimeException(s"missing definition  ${instance.id}"))
      }

      flowInstanceInit.pipeTo(context.actorOf(flowInstanceActorProps))(sender())
    }
  }

  def init: Cancellable = {
    log.info("initializing scheduler...")

    context.system.scheduler.schedule(initialDelay, tickInterval, self, FlowExecutorActor.Tick())(context.system.dispatcher)
  }
}

