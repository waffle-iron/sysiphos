package com.flowtick.sysiphos.task

import com.flowtick.sysiphos.flow.FlowTask

case class CommandLineTask(
  id: String,
  children: Option[Seq[FlowTask]],
  command: String) extends FlowTask
