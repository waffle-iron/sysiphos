package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneOffset }

import com.flowtick.sysiphos.scheduler.{ CronSchedule, FlowSchedule, FlowScheduler }
import cron4s.Cron
import cron4s.lib.javatime._

object CronScheduler extends FlowScheduler {
  val offset: ZoneOffset = ZoneOffset.UTC

  def toDateTime(epoch: Long): LocalDateTime = LocalDateTime.ofEpochSecond(epoch, 0, offset)

  override def nextOccurrence(schedule: FlowSchedule, now: Long): Option[Long] = schedule match {
    case cron: CronSchedule =>
      val dateTime = toDateTime(now)
      Cron(cron.expression).toOption.flatMap(_.next(dateTime)).map(_.toEpochSecond(offset))
    case _ => None
  }
}
