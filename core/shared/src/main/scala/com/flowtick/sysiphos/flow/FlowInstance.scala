package com.flowtick.sysiphos.flow

final case class FlowInstanceContextValue(key: String, value: String)

final case class FlowInstanceDetails(
  id: String,
  flowDefinitionId: String,
  creationTime: Long,
  startTime: Option[Long],
  endTime: Option[Long],
  retries: Int,
  status: String,
  context: Seq[FlowInstanceContextValue]) extends FlowInstance

trait FlowInstance extends FlowExecutable {
  def retries: Int

  def status: String

  def context: Seq[FlowInstanceContextValue]
}
