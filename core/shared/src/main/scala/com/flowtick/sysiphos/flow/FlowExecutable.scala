package com.flowtick.sysiphos.flow

trait FlowExecutable {
  def id: String
  def creationTime: Long
  def startTime: Option[Long]
  def endTime: Option[Long]
}
