package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneOffset }
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, Cancellable, Props }
import com.flowtick.sysiphos.execution.FlowInstanceActor.CreateInstance
import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository, FlowScheduler }
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.duration.FiniteDuration

object AkkaFlowExecutor {
  case class Init()
  case class Tick()
}

trait AkkaFlowExecution {
  val flowScheduleRepository: FlowScheduleRepository
  val flowScheduler: FlowScheduler
  implicit val taskScheduler: Scheduler

  def createInstance(flowSchedule: FlowSchedule): Unit

  def tick(now: Long): Task[Unit] =
    Task.fromFuture(flowScheduleRepository.getFlowSchedules.map(_.filter(_.enabled))).map { schedules =>
      schedules.foreach { schedule =>
        schedule.nextDueDate match {
          case Some(timestamp) if timestamp <= now =>
            createInstance(schedule)
          case None =>
            flowScheduler.nextOccurrence(schedule, now).foreach(next => flowScheduleRepository.setDueDate(schedule, next))
          case _ =>
        }
      }
    }
}

class AkkaFlowExecutor(
  val flowScheduleRepository: FlowScheduleRepository,
  val flowScheduler: FlowScheduler,
  instanceActorProps: Props,
  val taskScheduler: Scheduler) extends Actor with AkkaFlowExecution with Logging {

  val initialDelay = FiniteDuration(10000, TimeUnit.MILLISECONDS)
  val tickInterval = FiniteDuration(10000, TimeUnit.MILLISECONDS)

  def now: LocalDateTime = LocalDateTime.now()

  def zoneOffset: ZoneOffset = ZoneOffset.UTC

  override def receive: PartialFunction[Any, Unit] = {
    case _: AkkaFlowExecutor.Init => init
    case _: AkkaFlowExecutor.Tick => tick(now.toEpochSecond(zoneOffset))
  }

  def init: Cancellable = {
    log.info("initializing scheduler...")

    context.system.scheduler.schedule(initialDelay, tickInterval, self, AkkaFlowExecutor.Tick())(context.system.dispatcher)
  }

  override def createInstance(flowSchedule: FlowSchedule): Unit = {
    context.actorOf(instanceActorProps) ! CreateInstance(flowSchedule.flowDefinitionId)
  }
}

