package com.flowtick.sysiphos.scheduler

trait FlowSchedule {
  def id: String
  def flowDefinitionId: String
  def flowTaskId: Option[String]
  def nextDueDate: Option[Long]
  def enabled: Option[Boolean]
  def expression: Option[String]
}

final case class FlowScheduleDetails(
  id: String,
  creator: String,
  created: Long,
  version: Long,
  updated: Option[Long],
  expression: Option[String],
  flowDefinitionId: String,
  flowTaskId: Option[String],
  nextDueDate: Option[Long],
  enabled: Option[Boolean]) extends FlowSchedule