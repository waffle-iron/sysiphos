package com.flowtick.sysiphos

trait FlowTask {
  def id: String
  def children: Seq[FlowTask]
}

trait FlowDefinition {
  def id: String
  def task: FlowTask
}

case class SysiphosDefinition(id: String, task: FlowTask) extends FlowDefinition
case class SysiphosTask(id: String, children: Seq[FlowTask]) extends FlowTask

