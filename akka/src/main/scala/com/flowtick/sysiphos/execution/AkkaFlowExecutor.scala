package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneOffset }
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, Cancellable }
import com.flowtick.sysiphos.flow.{ FlowInstance, FlowInstanceRepository }
import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository, FlowScheduler }
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.duration.FiniteDuration

object AkkaFlowExecutor {
  case class Init()
  case class Tick()
}

trait AkkaFlowExecution extends Logging {
  val flowScheduleRepository: FlowScheduleRepository
  val flowInstanceRepository: FlowInstanceRepository
  val flowScheduler: FlowScheduler
  implicit val taskScheduler: Scheduler

  def createInstance(flowSchedule: FlowSchedule): Task[FlowInstance] = {
    log.debug(s"creating instance for $flowSchedule.")
    Task.fromFuture(flowInstanceRepository.createFlowInstance(flowSchedule.flowDefinitionId, Map.empty))
  }

  def tick(now: Long): Task[Seq[Unit]] = {
    log.debug("tick.")
    val futureSchedules = flowScheduleRepository.getFlowSchedules.map(_.filter(_.enabled.contains(true)))
    Task.fromFuture(futureSchedules).flatMap { schedules =>
      log.debug(s"checking schedules: $schedules.")
      Task.gather(schedules.map(createInstanceIfDue(_, now)))
    }
  }

  def createInstanceIfDue(schedule: FlowSchedule, now: Long): Task[Unit] = {
    log.debug(s"checking if $schedule is due.")
    schedule.nextDueDate match {
      case Some(timestamp) if timestamp <= now =>
        createInstance(schedule).map(_ => ())
      case Some(_) =>
        log.debug(s"not due: $schedule")
        Task.unit
      case None =>
        flowScheduler
          .nextOccurrence(schedule, now)
          .map(next => Task.fromFuture(flowScheduleRepository.setDueDate(schedule, next)))
          .getOrElse(Task.unit)
    }
  }

}

class AkkaFlowExecutor(
  val flowScheduleRepository: FlowScheduleRepository,
  val flowInstanceRepository: FlowInstanceRepository,
  val flowScheduler: FlowScheduler,
  val taskScheduler: Scheduler) extends Actor with AkkaFlowExecution with Logging {

  val initialDelay = FiniteDuration(10000, TimeUnit.MILLISECONDS)
  val tickInterval = FiniteDuration(10000, TimeUnit.MILLISECONDS)

  def now: LocalDateTime = LocalDateTime.now()

  def zoneOffset: ZoneOffset = ZoneOffset.UTC

  override def receive: PartialFunction[Any, Unit] = {
    case _: AkkaFlowExecutor.Init => init
    case _: AkkaFlowExecutor.Tick => tick(now.toEpochSecond(zoneOffset)).runAsync(taskScheduler)
  }

  def init: Cancellable = {
    log.info("initializing scheduler...")

    context.system.scheduler.schedule(initialDelay, tickInterval, self, AkkaFlowExecutor.Tick())(context.system.dispatcher)
  }
}

