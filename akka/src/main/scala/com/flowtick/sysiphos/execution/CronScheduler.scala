package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneOffset }

import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduler }
import cron4s.Cron
import cron4s.lib.javatime._

import com.flowtick.sysiphos._

object CronScheduler extends FlowScheduler with Logging {
  val offset: ZoneOffset = ZoneOffset.UTC

  def toDateTime(epoch: Long): LocalDateTime = LocalDateTime.ofEpochSecond(epoch, 0, offset)

  override def nextOccurrence(schedule: FlowSchedule, now: Long): Option[Long] = {
    schedule.expression
      .flatMap(Cron(_).toOption)
      .flatMap(_.next(toDateTime(now)))
      .map(_.toEpochSecond(offset))
  }
}
