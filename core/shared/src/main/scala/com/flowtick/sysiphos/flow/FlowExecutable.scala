package com.flowtick.sysiphos.flow

trait FlowExecutable {
  def id: String
  def flowDefinitionId: String
  def creationTime: Long // in epoch seconds
  def startTime: Option[Long] // in epoch seconds
  def endTime: Option[Long] // in epoch seconds
}
