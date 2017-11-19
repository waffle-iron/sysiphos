package com.flowtick.sysiphos.flow

trait Executable {
  def id: String
  def creationTime: Long
  def startTime: Option[Long]
  def endTime: Option[Long]
}

trait FlowInstance extends Executable {
  def context: Map[String, String]
}

trait FlowTaskInstance extends Executable {
  def flowInstance: FlowInstance
}

case class SysiphosFlowInstance(
  id: String,
  creationTime: Long,
  startTime: Option[Long] = None,
  endTime: Option[Long] = None,
  context: Map[String, String] = Map.empty) extends FlowInstance

case class SysiphosFlowTaskInstance(
  id: String,
  creationTime: Long,
  flowInstance: FlowInstance,
  startTime: Option[Long] = None,
  endTime: Option[Long] = None) extends FlowTaskInstance