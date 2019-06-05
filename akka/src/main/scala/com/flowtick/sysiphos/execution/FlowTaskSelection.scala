package com.flowtick.sysiphos.execution

sealed trait FlowTaskSelection
final case class TaskId(id: String) extends FlowTaskSelection
final case class OnFailureId(id: String) extends FlowTaskSelection
case object PendingTasks extends FlowTaskSelection

