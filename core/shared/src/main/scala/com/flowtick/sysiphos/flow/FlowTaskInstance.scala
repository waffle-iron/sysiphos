package com.flowtick.sysiphos.flow

trait FlowTaskInstance {
  def id: String
  def flowInstanceId: String
  def taskId: String
  def creationTime: Long
  def startTime: Option[Long]
  def endTime: Option[Long]
  def retries: Int
  def status: String
}

final case class FlowTaskInstanceDetails(
  id: String,
  flowInstanceId: String,
  taskId: String,
  creationTime: Long,
  startTime: Option[Long] = None,
  endTime: Option[Long] = None,
  retries: Int,
  status: String) extends FlowTaskInstance