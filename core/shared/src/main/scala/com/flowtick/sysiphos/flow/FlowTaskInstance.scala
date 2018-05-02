package com.flowtick.sysiphos.flow

trait FlowTaskInstance extends FlowExecutable {
  def flowInstance: FlowInstance
}

case class SysiphosFlowTaskInstance(
  id: String,
  flowDefinitionId: String,
  creationTime: Long,
  flowInstance: FlowInstance,
  startTime: Option[Long] = None,
  endTime: Option[Long] = None) extends FlowTaskInstance