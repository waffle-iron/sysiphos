package com.flowtick.sysiphos.task

import com.flowtick.sysiphos.flow.{ FlowInstanceContextValue, FlowTask }

final case class TriggerFlowTask(
  id: String,
  `type`: String = "trigger",
  flowDefinitionId: String,
  children: Option[Seq[FlowTask]],
  startDelay: Option[Long] = None,
  retryDelay: Option[Long] = None,
  retries: Option[Int] = None,
  context: Option[Seq[FlowInstanceContextValue]] = None,
  onFailure: Option[FlowTask] = None) extends FlowTask
