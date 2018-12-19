package com.flowtick.sysiphos.task

import com.flowtick.sysiphos.flow.FlowTask

final case class CommandLineTask(
  id: String,
  children: Option[Seq[FlowTask]] = None,
  command: String,
  `type`: String = "shell",
  shell: Option[String] = None,
  startDelay: Option[Long] = None,
  retryDelay: Option[Long] = None,
  retries: Option[Int] = None) extends FlowTask