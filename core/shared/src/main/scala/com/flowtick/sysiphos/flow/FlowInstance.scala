package com.flowtick.sysiphos.flow

trait FlowInstance extends FlowExecutable {
  def status: String
  def context: Map[String, String]
}