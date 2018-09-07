package com.flowtick.sysiphos.flow

final case class FlowInstanceContextValue(key: String, value: String)

object FlowInstanceStatus extends Enumeration {
  type FlowInstanceStatus = Value
  val New: FlowInstanceStatus.Value = Value("new")
  val Done: FlowInstanceStatus.Value = Value("done")
  val Failed: FlowInstanceStatus.Value = Value("failed")
  val Running: FlowInstanceStatus.Value = Value("running")
}

final case class FlowInstanceDetails(
  id: String,
  flowDefinitionId: String,
  creationTime: Long,
  startTime: Option[Long],
  endTime: Option[Long],
  retries: Int,
  status: FlowInstanceStatus.FlowInstanceStatus,
  context: Seq[FlowInstanceContextValue]) extends FlowInstance

trait FlowInstance extends FlowExecutable {
  def retries: Int
  def status: FlowInstanceStatus.FlowInstanceStatus
  def context: Seq[FlowInstanceContextValue]
}
