package com.flowtick.sysiphos.scheduler

trait FlowSchedule {
  def id: String
  def flowDefinitionId: String
  def flowTaskId: Option[String]
  def nextDueDate: Option[Long]
  def enabled: Boolean
}

object FlowSchedule {
  import io.circe.generic.auto._
  import io.circe.parser._
  import io.circe.syntax._

  def fromJson(json: String): Either[Exception, CronSchedule] = {
    decode[CronSchedule](json)
  }

  def toJson(definition: FlowSchedule): String = definition match {
    case s: CronSchedule => s.asJson.noSpaces
  }
}
