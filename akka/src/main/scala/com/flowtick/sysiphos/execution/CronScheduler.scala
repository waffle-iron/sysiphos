package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduler }
import cron4s.Cron
import cron4s.lib.javatime._
import com.flowtick.sysiphos._
import com.flowtick.sysiphos.core.Clock
import fs2.Pure

object CronScheduler extends FlowScheduler with Logging with Clock {

  override def nextOccurrence(schedule: FlowSchedule, now: Long): Option[Long] = {
    schedule.expression
      .flatMap(Cron(_).toOption)
      .flatMap(_.next(fromEpochSeconds(now)))
      .map(_.toEpochSecond)
  }

  def missedOccurrences(schedule: FlowSchedule, now: Long): Seq[Long] = {
    def nextDates(dueDate: Long): fs2.Stream[Pure, Long] = nextOccurrence(schedule, dueDate).map { first =>
      fs2.Stream.unfold(first)(previous => {
        nextOccurrence(schedule, previous).flatMap(next => Some(previous, next))
      })
    }.getOrElse(fs2.Stream.empty)

    schedule.nextDueDate
      .map(nextDates)
      .getOrElse(fs2.Stream.empty)
      .takeWhile(_ < now)
      .compile.toList
  }

}
