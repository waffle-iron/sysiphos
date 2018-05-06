package com.flowtick.sysiphos.scheduler

trait FlowScheduler {
  def nextOccurrence(schedule: FlowSchedule, now: Long): Option[Long]
}