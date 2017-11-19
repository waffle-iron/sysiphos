package com.flowtick.sysiphos.execution

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import com.flowtick.sysiphos.scheduler.FlowScheduleRepository
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.duration.FiniteDuration

case class Init()
case class Tick()

class AkkaFlowExecutor(flowScheduleRepository: FlowScheduleRepository)(implicit taskScheduler: Scheduler) extends Actor {

  val initialDelay = FiniteDuration(10000, TimeUnit.MILLISECONDS)
  val tickInterval = FiniteDuration(10000, TimeUnit.MILLISECONDS)

  override def receive = {
    case _: Init => init
    case _: Tick => tick
  }

  def init = {
    println("initializing schedules...")
    for {
      schedules <- Task.fromFuture(flowScheduleRepository.getFlowSchedules())
      schedule <- schedules.filter(_.enabled)
    } {
      println(schedule)
    }

    context.system.scheduler.schedule(initialDelay, tickInterval, self, Tick())(context.system.dispatcher)
  }

  def tick = println("tick")
}
