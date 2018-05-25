package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneOffset }
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, Cancellable, Props }
import akka.pattern.pipe
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.AkkaFlowExecutor.DueFlowDefinitions
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object AkkaFlowExecutor {
  case class Init()
  case class Tick()
  case class DueFlowDefinitions(flows: Seq[FlowDefinition])
}

trait AkkaFlowExecution extends Logging {
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
    val futureSchedules: Future[Seq[FlowSchedule]] = flowScheduleRepository
      .getFlowSchedules.map(_.filter(_.enabled.contains(true)))

    futureSchedules.flatMap { schedules =>
      log.debug(s"checking sched" +
        s"ules: $schedules.")
      Future.sequence {
        schedules.map(s => createInstanceIfDue(s, now))
      }
    }
  }

  def createInstanceIfDue(schedule: FlowSchedule, now: Long): Future[Option[FlowInstance]] = {
    log.debug(s"checking if $schedule is due.")
    schedule.nextDueDate match {
      case Some(timestamp) if timestamp <= now =>
        createInstance(schedule).map(Option(_))
      case Some(_) =>
        log.debug(s"not due: $schedule")
        Future.successful(None)
      case None =>
        flowScheduler
          .nextOccurrence(schedule, now)
          .map(next => flowScheduleStateStore.setDueDate(schedule.id, next))
          .getOrElse(Future.successful())
        Future.successful(None)
    }
  }

}

class AkkaFlowExecutor(
  val flowScheduleRepository: FlowScheduleRepository[FlowSchedule],
  val flowInstanceRepository: FlowInstanceRepository[FlowInstance],
  val flowDefinitionRepository: FlowDefinitionRepository,
  val flowScheduleStateStore: FlowScheduleStateStore,
  val flowScheduler: FlowScheduler) extends Actor with AkkaFlowExecution with Logging {

  val initialDelay = FiniteDuration(10000, TimeUnit.MILLISECONDS)
  val tickInterval = FiniteDuration(10000, TimeUnit.MILLISECONDS)

  def now: LocalDateTime = LocalDateTime.now()

  def zoneOffset: ZoneOffset = ZoneOffset.UTC

  override implicit val repositoryContext: RepositoryContext = new RepositoryContext {
    override def currentUser: String = "undefined"
  }

  override def receive: PartialFunction[Any, Unit] = {
    case _: AkkaFlowExecutor.Init => init
    case _: AkkaFlowExecutor.Tick =>
      val futureTaskInstances = dueTaskInstances(now.toEpochSecond(zoneOffset))
      val futureFlowDefinitions = futureTaskInstances.flatMap { taskInstances =>
        Future.sequence(taskInstances.map {
          case Some(taskInstance) =>
            flowDefinitionRepository.getFlowDefinitions.map(_.find(_.id == taskInstance.flowDefinitionId))
        }).map(maybeFlowDefinitions => DueFlowDefinitions(maybeFlowDefinitions.flatten))
      }.recover { case e: Exception =>
        log.error(s"error while preparing flow definitions: ${e.getLocalizedMessage}")
        DueFlowDefinitions(Seq.empty)
      }

      futureFlowDefinitions.pipeTo(self)(sender())

    case DueFlowDefinitions(flowDefinitions) => flowDefinitions.foreach { flowDefinition =>
      val executorActorProps = Props(classOf[AkkaFlowDefinitionExecutor], flowDefinition)
      log.info(s"starting a flow definition actor for $flowDefinition")
      context.actorOf(executorActorProps) ! FlowDefinitionExecutor.Init
    }
  }

  def init: Cancellable = {
    log.info("initializing scheduler...")

    context.system.scheduler.schedule(initialDelay, tickInterval, self, AkkaFlowExecutor.Tick())(context.system.dispatcher)
  }
}

