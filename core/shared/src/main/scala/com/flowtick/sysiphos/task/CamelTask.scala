package com.flowtick.sysiphos.task

import com.flowtick.sysiphos.flow.FlowTask

final case class RegistryEntry(`type`: String, fqn: String, properties: Map[String, String])

final case class CamelTask(
  id: String,
  `type`: String = "camel",
  uri: String,
  pattern: Option[String] = None,
  bodyTemplate: Option[String] = None,
  headers: Option[Map[String, String]] = None,
  children: Option[Seq[FlowTask]],
  registry: Option[Map[String, RegistryEntry]] = None) extends FlowTask