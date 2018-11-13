package com.flowtick.sysiphos.task

import com.flowtick.sysiphos.flow.FlowTask

final case class CommandLineTask(
  id: String,
  children: Option[Seq[FlowTask]],
  command: String,
  `type`: String = "shell",
  shell: Option[String] = None) extends FlowTask

final case class CamelTask(
  id: String,
  `type`: String = "camel",
  uri: String,
  pattern: Option[String] = None,
  bodyTemplate: Option[String] = None,
  headers: Option[Map[String, String]] = None,
  children: Option[Seq[FlowTask]]) extends FlowTask

final case class TriggerFlowTask(
  id: String,
  `type`: String = "trigger",
  flowDefinitionId: String,
  children: Option[Seq[FlowTask]]) extends FlowTask
