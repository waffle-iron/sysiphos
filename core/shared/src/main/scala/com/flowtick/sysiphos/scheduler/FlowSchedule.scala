package com.flowtick.sysiphos.scheduler

trait FlowSchedule {
  def id: String
  def flowDefinitionId: String
  def flowTaskId: Option[String] = None
  def nextDueDate: Option[Long] = None
  def enabled: Boolean = false
}
