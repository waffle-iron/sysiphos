package com.flowtick.sysiphos.execution

import cats.effect.IO
import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduler }
import cron4s.Cron
import cron4s.lib.javatime._
import com.flowtick.sysiphos._
import com.flowtick.sysiphos.core.Clock
import fs2._

object CronScheduler extends FlowScheduler with Logging with Clock {

  override def nextOccurrence(schedule: FlowSchedule, now: Long): Either[Throwable, Long] = for {
    cronExpr <- schedule
      .expression
      .map(Cron(_))
      .getOrElse(Left(new IllegalArgumentException("no schedule defined")))

    nextDue <- cronExpr
      .next(fromEpochSeconds(now))
      .map(nextDate => Right(nextDate.toEpochSecond))
      .getOrElse(Left(new IllegalStateException(s"no next date for cron expression $cronExpr")))
  } yield nextDue

  def missedOccurrences(schedule: FlowSchedule, now: Long): Either[Throwable, List[Long]] = {
    def nextDates(dueDate: Long): fs2.Stream[Pure, Either[Throwable, Long]] =
      fs2.Stream.unfold(nextOccurrence(schedule, dueDate))(previous => {
        Some(previous, previous.flatMap(nextOccurrence(schedule, _)))
      })

    val next: fs2.Stream[IO, Long] = schedule.nextDueDate
      .map(nextDates)
      .getOrElse(fs2.Stream.empty)
      .takeWhile {
        case Right(epoch) => epoch < now
        case Left(_) => false
      }
      .covary[IO]
      .flatMap {
        case Right(epoch) => fs2.Stream.emit(epoch)
        case Left(error) => fs2.Stream.raiseError[IO](error).covaryOutput[Long]
      }

    next.compile.toList.attempt.unsafeRunSync()
  }

}
