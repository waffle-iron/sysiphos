package com.flowtick.sysiphos.scheduler

trait FlowScheduler {
  def nextOccurrence(schedule: FlowSchedule, now: Long): Either[Throwable, Long]

  def missedOccurrences(schedule: FlowSchedule, now: Long): Either[Throwable, List[Long]]
}