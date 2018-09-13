package com.flowtick.sysiphos.ui

import com.flowtick.sysiphos.flow.{ FlowDefinitionDetails, FlowDefinitionSummary, FlowInstanceDetails, FlowTaskInstanceDetails }
import com.flowtick.sysiphos.scheduler.FlowScheduleDetails
import io.circe.{ Decoder, Json }
import io.circe.generic.auto._
import io.circe.parser._
import org.scalajs.dom.ext.{ Ajax, AjaxException }

import scala.concurrent.{ ExecutionContext, Future }

case class FlowDefinitionDetailsResult(definition: Option[FlowDefinitionDetails])
case class FlowDefinitionList(definitions: Seq[FlowDefinitionSummary])
case class FlowScheduleList(schedules: Seq[FlowScheduleDetails])
case class FlowInstanceList(instances: Seq[FlowInstanceDetails])

case class OverviewQueryResult(instances: Seq[FlowInstanceDetails], taskInstances: Seq[FlowTaskInstanceDetails])
case class FlowInstanceOverview(instance: FlowInstanceDetails, tasks: Seq[FlowTaskInstanceDetails])

case class CreateOrUpdateFlowResult[T](createOrUpdateFlowDefinition: T)
case class CreateFlowScheduleResult[T](createFlowSchedule: T)

case class EnableResult(enabled: Boolean)
case class ExpressionResult(expression: String)
case class UpdateFlowScheduleResponse[T](updateFlowSchedule: T)

case class GraphQLResponse[T](data: T)

trait SysiphosApi {
  def getFlowDefinitions: Future[FlowDefinitionList]

  def getFlowDefinition(id: String): Future[Option[FlowDefinitionDetails]]

  def createOrUpdateFlowDefinition(source: String): Future[Option[FlowDefinitionDetails]]

  def createFlowSchedule(flowId: String, expression: String): Future[FlowScheduleDetails]

  def getSchedules(flowId: Option[String]): Future[FlowScheduleList]

  def setFlowScheduleEnabled(
    id: String,
    enabled: Boolean): Future[Boolean]

  def setFlowScheduleExpression(
    id: String,
    expression: String): Future[String]

  def getInstances(flowId: Option[String], status: Option[String], createdGreaterThan: Option[Long]): Future[FlowInstanceList]

  def getInstanceOverview(instanceId: String): Future[Option[FlowInstanceOverview]]
}

class SysiphosApiClient(implicit executionContext: ExecutionContext) extends SysiphosApi {
  def quotedOrNull(optional: Option[String]): String = optional.map("\"" + _ + "\"").getOrElse("null")

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

  override def getSchedules(flowId: Option[String]): Future[FlowScheduleList] =
    query[FlowScheduleList](
      s"""
         |{
         |  schedules (flowId: ${quotedOrNull(flowId)})
         |  {id, creator, created, version, flowDefinitionId, enabled, expression, nextDueDate }
         |}
         |
       """.stripMargin).map(_.data)

  override def getFlowDefinitions: Future[FlowDefinitionList] =
    query[FlowDefinitionList]("{ definitions {id, counts { status, count, flowDefinitionId } } }").map(_.data)

  override def getFlowDefinition(id: String): Future[Option[FlowDefinitionDetails]] = {
    query[FlowDefinitionDetailsResult](s"""{ definition(id: "$id") {id, version, source, created} }""").map(_.data.definition)
  }

  override def createOrUpdateFlowDefinition(source: String): Future[Option[FlowDefinitionDetails]] =
    parse(source) match {
      case Right(json) =>
        val createFlowQuery = s"""
                    |mutation {
                    |  createOrUpdateFlowDefinition(json: ${Json.fromString(json.noSpaces).noSpaces}) {
                    |    id, version, source, created
                    |  }
                    |}
                    |""".stripMargin
        query[CreateOrUpdateFlowResult[Option[FlowDefinitionDetails]]](createFlowQuery).map(_.data.createOrUpdateFlowDefinition)
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

  override def createFlowSchedule(flowId: String, expression: String): Future[FlowScheduleDetails] = {
    val createScheduleQuery =
      s"""
         |mutation {
         |  createFlowSchedule(flowDefinitionId: "$flowId", expression: "$expression") {
         |    id, creator, created, version, flowDefinitionId, enabled, expression, nextDueDate
         |  }
         |}
       """.stripMargin
    query[CreateFlowScheduleResult[FlowScheduleDetails]](createScheduleQuery).map(_.data.createFlowSchedule)
  }

  override def getInstances(flowId: Option[String], status: Option[String], createdGreaterThan: Option[Long]): Future[FlowInstanceList] = {
    val instancesQuery =
      s"""
         |{
         |  instances (flowDefinitionId: ${quotedOrNull(flowId)},
         |             status: ${quotedOrNull(status)},
         |             createdGreaterThan: ${createdGreaterThan.map(_.toString).getOrElse("null")}) {
         |    id, flowDefinitionId, creationTime, startTime, endTime, retries, status, context {
         |      key, value
         |    }
         |  }
         |}
       """.stripMargin
    query[FlowInstanceList](instancesQuery).map(_.data)
  }

  override def getInstanceOverview(instanceId: String): Future[Option[FlowInstanceOverview]] = {
    val instanceOverviewQuery =
      s"""
         |{
         |  instances(instanceIds: ["$instanceId"]) {
         |    id, flowDefinitionId, creationTime, startTime, endTime, retries, status, context {
         |      key, value
         |    }
         |  },
         |	taskInstances(flowInstanceId: "$instanceId") {
         |    id, flowInstanceId, taskId, creationTime, updatedTime, startTime, endTime, status, retries
         |  }
         |}
       """.stripMargin

    def resultToOverview(result: OverviewQueryResult): Option[FlowInstanceOverview] =
      result.instances.headOption.flatMap(details => {
        Some(FlowInstanceOverview(details, result.taskInstances))
      })

    query[OverviewQueryResult](instanceOverviewQuery).map(result => resultToOverview(result.data))
  }
}
