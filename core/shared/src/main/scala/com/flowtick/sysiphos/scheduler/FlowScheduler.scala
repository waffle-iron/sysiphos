package com.flowtick.sysiphos.scheduler

trait FlowScheduler {
  def nextOccurrence(schedule: FlowSchedule, now: Long): Option[Long]
}

final case class CronSchedule(
  id: String,
  expression: String,
  flowDefinitionId: String,
  flowTaskId: Option[String] = None,
  nextDueDate: Option[Long] = None,
  enabled: Option[Boolean] = Some(false)) extends FlowSchedule
