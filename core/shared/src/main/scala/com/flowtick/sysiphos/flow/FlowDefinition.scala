package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.task.CommandLineTask
import io.circe._

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
  import io.circe.syntax._

  implicit val taskDecoder: Decoder[FlowTask] = (c: HCursor) => for {
    id <- c.downField("id").as[String]
    typeHint <- c.downField("type").as[String]
    task <- taskFromCursor(typeHint, c)
  } yield task

  implicit val taskEncoder: Encoder[FlowTask] = new Encoder[FlowTask] {
    override def apply(a: FlowTask): Json = a match {
      case cmd: CommandLineTask => Json.obj(
        "id" -> Json.fromString(cmd.id),
        "type" -> Json.fromString("shell"),
        "command" -> Json.fromString(cmd.command),
        "children" -> cmd.children.asJson)
      case _ => Json.Null
    }
  }

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

  def toJson(definition: FlowDefinition): String = definition match {
    case s: SysiphosDefinition => s.asJson.noSpaces
  }
}

