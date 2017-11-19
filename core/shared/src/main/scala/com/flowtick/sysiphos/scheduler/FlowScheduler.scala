package com.flowtick.sysiphos.scheduler

trait FlowScheduler {
  def nextOccurrence(schedule: FlowSchedule, now: Long): Long
}

final case class CronSchedule(
  id: String,
  expression: String,
  flowDefinitionId: String) extends FlowSchedule
