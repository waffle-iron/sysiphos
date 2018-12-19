package com.flowtick.sysiphos.execution.task

import com.flowtick.sysiphos.flow.FlowInstanceContextValue

import scala.beans.BeanProperty

// for better compat. with Jackson from Camel
class TaskConfigPropertyDto(@BeanProperty var key: String, @BeanProperty var value: String) {
  def this() = this(null, null)
}

class TaskConfigurationDto(
  @BeanProperty var id: String,
  @BeanProperty var businessKey: String,
  @BeanProperty var properties: java.util.List[TaskConfigPropertyDto]) {
  def this() = this(null, null, null)
}

final case class TaskConfiguration(id: String, businessKey: String, contextValues: Seq[FlowInstanceContextValue])
final case class TaskConfigurations(
  limit: Option[Long],
  offset: Option[Long],
  configurations: Seq[TaskConfiguration])