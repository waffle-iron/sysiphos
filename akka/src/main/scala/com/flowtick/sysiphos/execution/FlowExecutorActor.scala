package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneOffset }
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, Cancellable, Props }
import akka.pattern.pipe
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowExecutorActor.DueFlowDefinitions
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object FlowExecutorActor {
  case class Init()
  case class Tick()
  case class DueFlowDefinitions(flows: Seq[FlowDefinition])
}

trait FlowExecution extends Logging {
  val flowScheduleRepository: FlowScheduleRepository[FlowSchedule]
  val flowInstanceRepository: FlowInstanceRepository[FlowInstance]
  val flowScheduleStateStore: FlowScheduleStateStore
  val flowScheduler: FlowScheduler
  implicit val repositoryContext: RepositoryContext

  def createInstance(flowSchedule: FlowSchedule): Future[FlowInstance] = {
    log.debug(s"creating instance for $flowSchedule.")
    flowInstanceRepository.createFlowInstance(flowSchedule.flowDefinitionId, Map.empty)
  }

  def dueTaskInstances(now: Long): Future[Seq[Option[FlowInstance]]] = {
    log.debug("tick.")
    val futureEnabledSchedules: Future[Seq[FlowSchedule]] = flowScheduleRepository
      .getFlowSchedules.map(_.filter(_.enabled.contains(true)))

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
    }
  }

  def createInstanceIfDue(schedule: FlowSchedule, now: Long): Future[Option[FlowInstance]] = {
    log.debug(s"checking if $schedule is due.")
    schedule.nextDueDate match {
      case Some(timestamp) if timestamp <= now =>
        createInstance(schedule).map(Option(_))
      case _ =>
        log.debug(s"not due: $schedule")
        Future.successful(None)
    }
  }

}

class FlowExecutorActor(
  val flowScheduleRepository: FlowScheduleRepository[FlowSchedule],
  val flowInstanceRepository: FlowInstanceRepository[FlowInstance],
  val flowDefinitionRepository: FlowDefinitionRepository,
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
      val futureTaskInstances = dueTaskInstances(now.toEpochSecond(zoneOffset))
      val futureFlowDefinitions = futureTaskInstances.flatMap { taskInstances =>
        Future.sequence(taskInstances.map {
          case Some(taskInstance) =>
            flowDefinitionRepository.getFlowDefinitions.map(_.find(_.id == taskInstance.flowDefinitionId))
        }).map(maybeFlowDefinitions => DueFlowDefinitions(maybeFlowDefinitions.flatten))
      }.recover {
        case e: Exception =>
          log.error(s"error while preparing flow definitions: ${e.getLocalizedMessage}")
          DueFlowDefinitions(Seq.empty)
      }

      futureFlowDefinitions.pipeTo(self)(sender())

    case DueFlowDefinitions(flowDefinitions) => flowDefinitions.foreach { flowDefinition =>
      val executorActorProps = Props(new FlowInstanceExecutorActor(flowDefinition, flowInstanceRepository))
      log.info(s"starting a flow definition actor for $flowDefinition")
      context.actorOf(executorActorProps) ! FlowInstanceExecution.Init
    }
  }

  def init: Cancellable = {
    log.info("initializing scheduler...")

    context.system.scheduler.schedule(initialDelay, tickInterval, self, FlowExecutorActor.Tick())(context.system.dispatcher)
  }
}

