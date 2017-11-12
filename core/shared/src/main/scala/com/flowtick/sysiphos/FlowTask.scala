package com.flowtick.sysiphos

trait FlowTask {
  def id: String
}

case class Task(id: String, next: Option[FlowTask]) extends FlowTask
