package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneOffset }
import java.util.concurrent.TimeUnit

import akka.actor.{ Actor, Props }
import com.flowtick.sysiphos.scheduler.{ FlowScheduleRepository, FlowScheduler }
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.duration.FiniteDuration

case class Init()
case class Tick()

class AkkaFlowExecutor(
  flowScheduleRepository: FlowScheduleRepository,
  flowScheduler: FlowScheduler)(implicit taskScheduler: Scheduler) extends Actor {

  val initialDelay = FiniteDuration(10000, TimeUnit.MILLISECONDS)
  val tickInterval = FiniteDuration(10000, TimeUnit.MILLISECONDS)

  def now: LocalDateTime = LocalDateTime.now()

  def zoneOffset: ZoneOffset = ZoneOffset.UTC

  override def receive = {
    case _: Init => init
    case _: Tick => tick(now.toEpochSecond(zoneOffset))
  }

  def init = {
    println("initializing scheduler...")

    context.system.scheduler.schedule(initialDelay, tickInterval, self, Tick())(context.system.dispatcher)
  }

  def tick(now: Long) =
    Task.fromFuture(flowScheduleRepository.getFlowSchedules.map(_.filter(_.enabled))).map { schedules =>
      schedules.foreach { schedule =>
        context.child(schedule.flowDefinitionId).getOrElse(context.actorOf(Props(creator = new FlowInstanceActor)))
      }
    }

}

class FlowInstanceActor extends Actor {
  override def receive = {
    case _ =>
  }
}
