package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneOffset }

import com.flowtick.sysiphos.scheduler.{ CronSchedule, FlowSchedule, FlowScheduler }
import cron4s.Cron
import cron4s.lib.javatime._

object CronScheduler extends FlowScheduler {
  val offset = ZoneOffset.UTC

  def toDateTime(epoch: Long) = LocalDateTime.ofEpochSecond(epoch, 0, offset)

  override def nextOccurrence(schedule: FlowSchedule, now: Long): Option[Long] = schedule match {
    case CronSchedule(_, expression, _, _, _, _) =>
      val dateTime = toDateTime(now)
      Cron(expression).toOption.flatMap(_.next(dateTime)).map(_.toEpochSecond(offset))
    case _ => None
  }
}
