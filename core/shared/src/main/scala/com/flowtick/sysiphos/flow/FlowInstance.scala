package com.flowtick.sysiphos.flow

trait FlowInstance extends FlowExecutable {
  def retries: Int
  def status: String
  def context: Map[String, String]
}