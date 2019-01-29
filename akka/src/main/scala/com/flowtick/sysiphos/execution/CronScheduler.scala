package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduler }
import cron4s.Cron
import cron4s.lib.javatime._
import com.flowtick.sysiphos._
import com.flowtick.sysiphos.core.Clock

object CronScheduler extends FlowScheduler with Logging with Clock {

  override def nextOccurrence(schedule: FlowSchedule, now: Long): Option[Long] = {
    schedule.expression
      .flatMap(Cron(_).toOption)
      .flatMap(_.next(fromEpochSeconds(now)))
      .map(_.toEpochSecond(zoneOffset(currentTime)))
  }

  def missedOccurrences(schedule: FlowSchedule, now: Long): Seq[Long] = {

    def fill(next: Long, acc: Seq[Long]): Seq[Long] = {
      nextOccurrence(schedule, next).map { nextSchedule =>
        if (nextSchedule > now)
          acc
        else
          fill(nextSchedule, acc ++ Seq(nextSchedule))
      }.getOrElse(acc)
    }

    schedule.nextDueDate.map { next => fill(next, Seq.empty) }.getOrElse(Seq.empty)
  }

}
