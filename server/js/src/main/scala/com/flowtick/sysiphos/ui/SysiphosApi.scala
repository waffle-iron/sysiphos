package com.flowtick.sysiphos.ui

import com.flowtick.sysiphos.flow.{ FlowDefinitionDetails, FlowDefinitionSummary }
import com.flowtick.sysiphos.scheduler.FlowScheduleDetails
import io.circe.{ Decoder, Json }
import io.circe.generic.auto._
import io.circe.parser._
import org.scalajs.dom.ext.{ Ajax, AjaxException }

import scala.concurrent.{ ExecutionContext, Future }

case class FlowDefinitionDetailsResult(definition: Option[FlowDefinitionDetails])
case class FlowDefinitionList(definitions: Seq[FlowDefinitionSummary])
case class FlowScheduleList(schedules: Seq[FlowScheduleDetails])

case class CreateOrUpdateFlowResult[T](createOrUpdateFlowDefinition: T)

case class EnableResult(enabled: Boolean)
case class ExpressionResult(expression: String)
case class UpdateFlowScheduleResponse[T](updateFlowSchedule: T)

case class GraphQLResponse[T](data: T)

trait SysiphosApi {
  def getFlowDefinitions: Future[GraphQLResponse[FlowDefinitionList]]

  def getFlowDefinition(id: String): Future[Option[FlowDefinitionDetails]]

  def createOrUpdateFlowDefinition(source: String): Future[Option[FlowDefinitionDetails]]

  def getSchedules(flowId: Option[String]): Future[GraphQLResponse[FlowScheduleList]]

  def setFlowScheduleEnabled(
    id: String,
    enabled: Boolean): Future[Boolean]

  def setFlowScheduleExpression(
    id: String,
    expression: String): Future[String]
}

class SysiphosApiClient(implicit executionContext: ExecutionContext) extends SysiphosApi {
  def query[T](query: String, variables: Map[String, Json] = Map.empty)(implicit ev: Decoder[T]): Future[GraphQLResponse[T]] = {
    val queryJson = Json.obj(
      "query" -> Json.fromString(query),
      "variables" -> Json.fromFields(variables)).noSpaces

    Ajax.post("/api", queryJson).flatMap(response => decode[GraphQLResponse[T]](response.responseText) match {
      case Right(parsed) =>
        Future.successful(parsed)
      case Left(error) =>
        println(s"error while process api query: ${error.getMessage}, ${response.responseText}, ${response.status}, ${response.statusText}")
        error.printStackTrace()
        Future.failed(error)
    }).transform(identity[GraphQLResponse[T]], error => error match {
      case ajax: AjaxException =>
        val errorResponse: Json = decode[Json](ajax.xhr.responseText).toOption.flatMap(_.asObject).get("error").getOrElse(Json.fromString(error.getMessage))
        println(errorResponse)
        new RuntimeException(errorResponse.asString.getOrElse(""))
      case error: Throwable => new RuntimeException(error.getMessage)
    })
  }

  override def getSchedules(flowId: Option[String]): Future[GraphQLResponse[FlowScheduleList]] =
    query[FlowScheduleList]("{ schedules {id, creator, created, version, flowDefinitionId, enabled, expression, nextDueDate } }")

  override def getFlowDefinitions: Future[GraphQLResponse[FlowDefinitionList]] =
    query[FlowDefinitionList]("{ definitions {id, counts { status, count, flowDefinitionId } } }")

  override def getFlowDefinition(id: String): Future[Option[FlowDefinitionDetails]] = {
    query[FlowDefinitionDetailsResult](s"""{ definition(id: "$id") {id, version, source, created} }""").map(_.data.definition)
  }

  override def createOrUpdateFlowDefinition(source: String): Future[Option[FlowDefinitionDetails]] =
    parse(source) match {
      case Right(json) =>
        val queryString = s"""
                    |mutation {
                    |  createOrUpdateFlowDefinition(json: ${Json.fromString(json.noSpaces).noSpaces}) {
                    |    id, version, source, created
                    |  }
                    |}
                    |""".stripMargin
        query[CreateOrUpdateFlowResult[Option[FlowDefinitionDetails]]](queryString).map(_.data.createOrUpdateFlowDefinition)
      case Left(error) => Future.failed(error)
    }

  override def setFlowScheduleEnabled(
    id: String,
    enabled: Boolean): Future[Boolean] = {
    val queryString = s"""mutation { updateFlowSchedule(id: "$id", enabled: $enabled) { enabled } }"""
    query[UpdateFlowScheduleResponse[EnableResult]](queryString).map(_.data.updateFlowSchedule.enabled)
  }

  override def setFlowScheduleExpression(
    id: String,
    expression: String): Future[String] = {
    val queryString = s"""mutation { updateFlowSchedule(id: "$id", expression: "$expression") { expression } }"""
    query[UpdateFlowScheduleResponse[ExpressionResult]](queryString).map(_.data.updateFlowSchedule.expression)
  }
}
