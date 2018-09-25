package com.flowtick.sysiphos.task

import com.flowtick.sysiphos.flow.FlowTask

final case class CommandLineTask(
  id: String,
  children: Option[Seq[FlowTask]],
  command: String,
  `type`: String = "shell") extends FlowTask

final case class TriggerFlowTask(
  id: String,
  `type`: String = "trigger",
  flowDefinitionId: String,
  children: Option[Seq[FlowTask]]) extends FlowTask
