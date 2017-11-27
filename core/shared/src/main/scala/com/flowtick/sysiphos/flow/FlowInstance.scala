package com.flowtick.sysiphos.flow

trait FlowInstance extends FlowExecutable {
  def context: Map[String, String]
}

case class SysiphosFlowInstance(
  id: String,
  creationTime: Long,
  startTime: Option[Long] = None,
  endTime: Option[Long] = None,
  context: Map[String, String] = Map.empty) extends FlowInstance