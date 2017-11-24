package com.flowtick.sysiphos.scheduler

trait FlowSchedule {
  def id: String
  def flowDefinitionId: String
  def flowTaskId: Option[String]
  def nextDueDate: Option[Long]
  def enabled: Boolean
}
