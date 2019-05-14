package com.flowtick.sysiphos.task

import com.flowtick.sysiphos.flow.FlowDefinition.ItemSpec
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowTask }

final case class DefinitionImportTask(
  `type`: String = "definition-import",
  id: String,
  targetDefinitionId: Option[String] = None,
  definitionTemplate: Option[FlowDefinition] = None,
  fetchTask: CamelTask,
  items: ItemSpec,
  taskTemplate: FlowTask,
  children: Option[Seq[FlowTask]] = None,
  startDelay: Option[Long] = None,
  retryDelay: Option[Long] = None,
  retries: Option[Int] = None,
  onFailure: Option[FlowTask] = None) extends FlowTask

