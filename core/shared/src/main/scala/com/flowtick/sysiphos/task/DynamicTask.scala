package com.flowtick.sysiphos.task

import com.flowtick.sysiphos.flow.FlowDefinition.ItemSpec
import com.flowtick.sysiphos.flow.FlowTask

final case class DynamicTask(
  `type`: String = "dynamic",
  id: String,
  contextSourceUri: String,
  items: ItemSpec,
  children: Option[Seq[FlowTask]] = None,
  startDelay: Option[Long] = None,
  retryDelay: Option[Long] = None,
  retries: Option[Int] = None) extends FlowTask
