package com.flowtick.sysiphos.task

import com.flowtick.sysiphos.flow.FlowTask

final case class CommandLineTask(
  id: String,
  children: Option[Seq[FlowTask]],
  command: String,
  `type`: String = "shell",
  shell: Option[String] = None,
  startDelay: Option[Long] = None,
  retryDelay: Option[Long] = None,
  retries: Option[Int] = None) extends FlowTask

final case class TriggerFlowTask(
  id: String,
  `type`: String = "trigger",
  flowDefinitionId: String,
  children: Option[Seq[FlowTask]],
  startDelay: Option[Long] = None,
  retryDelay: Option[Long] = None,
  retries: Option[Int] = None) extends FlowTask
