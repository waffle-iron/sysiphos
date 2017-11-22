package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.task.CommandLineTask
import io.circe.{ Decoder, DecodingFailure, HCursor }

trait FlowTask {
  def id: String
  def children: Option[Seq[FlowTask]]
}

trait FlowDefinition {
  def id: String
  def task: FlowTask
}

object FlowDefinition {
  import io.circe.generic.auto._
  import io.circe.parser._

  implicit val taskDecoder: Decoder[FlowTask] = (c: HCursor) => for {
    id <- c.downField("id").as[String]
    typeHint <- c.downField("type").as[String]
    task <- taskFromCursor(typeHint, c)
  } yield task

  def taskFromCursor(typeHint: String, cursor: HCursor): Either[DecodingFailure, FlowTask] = {
    typeHint match {
      case "shell" => cursor.as[CommandLineTask]
      case _ => cursor.as[SysiphosTask]
    }
  }

  case class SysiphosDefinition(id: String, task: FlowTask) extends FlowDefinition
  case class SysiphosTask(
    id: String,
    `type`: String,
    children: Option[Seq[FlowTask]],
    properties: Option[Map[String, String]]) extends FlowTask

  def fromJson(json: String): Either[Exception, SysiphosDefinition] = {
    decode[SysiphosDefinition](json)
  }
}

