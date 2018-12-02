package com.flowtick.sysiphos.task

import com.flowtick.sysiphos.flow.FlowTask

final case class RegistryEntry(`type`: String, fqn: String, properties: Map[String, String])
final case class ExtractSpec(`type`: String, name: String, expression: String)

final case class CamelTask(
  id: String,
  children: Option[Seq[FlowTask]],
  uri: String,
  `type`: String = "camel",
  exchangeType: Option[String] = None,
  sendUri: Option[String] = None,
  receiveUri: Option[String] = None,
  pattern: Option[String] = None,
  bodyTemplate: Option[String] = None,
  headers: Option[Map[String, String]] = None,
  to: Option[Seq[String]] = None,
  extract: Option[Seq[ExtractSpec]] = None,
  convertStreamToString: Option[Boolean] = None,
  registry: Option[Map[String, RegistryEntry]] = None) extends FlowTask