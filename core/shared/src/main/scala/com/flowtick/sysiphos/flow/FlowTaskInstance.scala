package com.flowtick.sysiphos.flow

object FlowTaskInstanceStatus extends Enumeration {
  type FlowTaskInstanceStatus = Value
  val New: FlowTaskInstanceStatus.Value = Value("new")
  val Done: FlowTaskInstanceStatus.Value = Value("done")
  val Failed: FlowTaskInstanceStatus.Value = Value("failed")
  val Running: FlowTaskInstanceStatus.Value = Value("running")
}

trait FlowTaskInstance {
  def id: String
  def flowInstanceId: String
  def taskId: String
  def creationTime: Long
  def updatedTime: Option[Long]
  def startTime: Option[Long]
  def endTime: Option[Long]
  def retries: Int
  def status: FlowTaskInstanceStatus.FlowTaskInstanceStatus
  def retryDelay: Option[Long]
  def nextDueDate: Option[Long]
}

final case class FlowTaskInstanceDetails(
  id: String,
  flowInstanceId: String,
  taskId: String,
  creationTime: Long,
  updatedTime: Option[Long] = None,
  startTime: Option[Long] = None,
  endTime: Option[Long] = None,
  retries: Int,
  status: FlowTaskInstanceStatus.FlowTaskInstanceStatus,
  retryDelay: Option[Long],
  nextDueDate: Option[Long]) extends FlowTaskInstance