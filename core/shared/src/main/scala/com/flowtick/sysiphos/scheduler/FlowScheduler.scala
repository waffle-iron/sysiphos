package com.flowtick.sysiphos.scheduler

trait FlowScheduler {
  def nextOccurrence(schedule: FlowSchedule, now: Long): Option[Long]

  def missedOccurrences(schedule: FlowSchedule, now: Long): Seq[Long]

}