package com.flowtick.sysiphos.ui

import com.flowtick.sysiphos.flow.FlowTaskInstanceStatus.FlowTaskInstanceStatus
import com.flowtick.sysiphos.flow._
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
case class IdResult(id: String)

case class OverviewQueryResult(instances: Seq[FlowInstanceDetails], taskInstances: Seq[FlowTaskInstanceDetails])
case class FlowInstanceOverview(instance: FlowInstanceDetails, tasks: Seq[FlowTaskInstanceDetails])

case class CreateOrUpdateFlowResult[T](createOrUpdateFlowDefinition: T)
case class DeleteFlowResult[T](deleteFlowDefinition: T)
case class CreateFlowScheduleResult[T](createFlowSchedule: T)
case class CreateInstanceResult[T](createInstance: T)
case class DeleteInstanceResult[T](deleteInstance: T)
case class SetTaskStatusResult[T](setTaskStatus: T)
case class DeleteTaskInstanceResult[T](deleteFlowTaskInstance: T)

case class EnableResult(enabled: Boolean)
case class BackFillResult(backFill: Boolean)
case class ExpressionResult(expression: String)
case class UpdateFlowScheduleResponse[T](updateFlowSchedule: T)
case class LogResult(log: String)

case class GraphQLResponse[T](data: T)

case class ErrorMessage(message: String)
case class ErrorResponse(errors: Seq[ErrorMessage])

trait SysiphosApi {
  def getFlowDefinitions: Future[FlowDefinitionList]

  def getFlowDefinition(id: String): Future[Option[FlowDefinitionDetails]]

  def createOrUpdateFlowDefinition(source: String): Future[Option[FlowDefinitionDetails]]

  def deleteFlowDefinition(id: String): Future[String]

  def createFlowSchedule(flowId: String, expression: String): Future[FlowScheduleDetails]

  def getSchedules(flowId: Option[String]): Future[FlowScheduleList]

  def setFlowScheduleEnabled(
    id: String,
    enabled: Boolean): Future[Boolean]

  def setFlowScheduleBackFill(
    id: String,
    backFill: Boolean): Future[Boolean]

  def setFlowScheduleExpression(
    id: String,
    expression: String): Future[String]

  def setTaskStatus(taskInstanceId: String, status: FlowTaskInstanceStatus, retries: Int): Future[String]

  def getInstances(query: FlowInstanceQuery): Future[FlowInstanceList]

  def getInstanceOverview(instanceId: String): Future[Option[FlowInstanceOverview]]

  def getLog(logId: String): Future[String]

  def createInstance(flowDefinitionId: String, contextValues: Seq[FlowInstanceContextValue]): Future[String]

  def deleteInstance(flowInstanceId: String): Future[String]

}

class SysiphosApiClient(implicit executionContext: ExecutionContext) extends SysiphosApi {
  import com.flowtick.sysiphos.ui.vendor.ToastrSupport._

  def query[T](query: String, variables: Map[String, Json] = Map.empty)(implicit ev: Decoder[T]): Future[GraphQLResponse[T]] = {
    val queryJson = Json.obj(
      "query" -> Json.fromString(query),
      "variables" -> Json.fromFields(variables)).noSpaces

    Ajax.post("/api", queryJson, headers = Map("Content-Type" -> "application/json")).flatMap(response => decode[GraphQLResponse[T]](response.responseText) match {
      case Right(parsed) =>
        Future.successful(parsed)
      case Left(error) =>
        println(s"error while process api query: ${error.getMessage}, ${response.responseText}, ${response.status}, ${response.statusText}")
        error.printStackTrace()
        Future.failed(error)
    }).transform(identity[GraphQLResponse[T]], error => {
      val transformedError = error match {
        case ajax: AjaxException =>
          val errorMessage: String = decode[ErrorResponse](ajax.xhr.responseText)
            .map(_.errors.map(_.message).mkString(", "))
            .getOrElse(ajax.xhr.responseText)

          new RuntimeException(errorMessage)
        case error: Throwable =>
          new RuntimeException(error.getMessage)
      }

      println(transformedError.getMessage)

      transformedError
    })
  }.notifyError

  override def getSchedules(flowId: Option[String]): Future[FlowScheduleList] =
    query[FlowScheduleList](
      """
         |query($flowId: String) {
         |  schedules (flowId: $flowId)
         |  {id, creator, created, version, flowDefinitionId, enabled, expression, nextDueDate, backFill }
         |}
         |
       """.stripMargin, Map("flowId" -> flowId.map(Json.fromString).getOrElse(Json.Null))).map(_.data)

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

  override def setFlowScheduleBackFill(
    id: String,
    backFill: Boolean): Future[Boolean] = {
    val queryString = s"""mutation { updateFlowSchedule(id: "$id", backFill: $backFill) { backFill } }"""
    query[UpdateFlowScheduleResponse[BackFillResult]](queryString).map(_.data.updateFlowSchedule.backFill)
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

  override def getInstances(selection: FlowInstanceQuery): Future[FlowInstanceList] = {
    val instancesQuery =
      """
         |query($flowDefinitionId: String, $status: [String!], $createdGreaterThan: Long, $createdSmallerThan: Long) {
         |  instances (flowDefinitionId: $flowDefinitionId,
         |             status: $status,
         |             createdGreaterThan: $createdGreaterThan,
         |             createdSmallerThan: $createdSmallerThan) {
         |    id, flowDefinitionId, creationTime, startTime, endTime, status, context {
         |      key, value
         |    }
         |  }
         |}
       """.stripMargin

    val statusVariable: Json = selection
      .status
      .map(statuses => Json.fromValues(statuses.map(s => Json.fromString(s.toString))))
      .getOrElse(Json.Null)

    val definitionVariable = selection.flowDefinitionId.map(Json.fromString).getOrElse(Json.Null)
    val createdGreaterThan = selection.createdGreaterThan.map(Json.fromLong).getOrElse(Json.Null)
    val createdSmallerThan = selection.createdSmallerThan.map(Json.fromLong).getOrElse(Json.Null)

    query[FlowInstanceList](instancesQuery, Map(
      "flowDefinitionId" -> definitionVariable,
      "status" -> statusVariable,
      "createdGreaterThan" -> createdGreaterThan,
      "createdSmallerThan" -> createdSmallerThan)).map(_.data)
  }

  override def getInstanceOverview(instanceId: String): Future[Option[FlowInstanceOverview]] = {
    val instanceOverviewQuery =
      """
         |query($instanceId: String!) {
         |  instances(instanceIds: [$instanceId]) {
         |    id, flowDefinitionId, creationTime, startTime, endTime, status, context {
         |      key, value
         |    }
         |  },
         |	taskInstances(flowInstanceId: $instanceId) {
         |    id, flowInstanceId, flowDefinitionId, taskId, creationTime, updatedTime, startTime, endTime, status, retries, retryDelay, logId, nextDueDate
         |  }
         |}
       """.stripMargin

    def resultToOverview(result: OverviewQueryResult): Option[FlowInstanceOverview] =
      result.instances.headOption.flatMap(details => {
        Some(FlowInstanceOverview(details, result.taskInstances))
      })

    query[OverviewQueryResult](instanceOverviewQuery, variables = Map("instanceId" -> Json.fromString(instanceId)))
      .map(result => resultToOverview(result.data))
  }

  override def getLog(logId: String): Future[String] = {
    query[LogResult](s"""{ log (logId: "$logId") }""").map(_.data.log)
  }

  override def createInstance(flowDefinitionId: String, contextValues: Seq[FlowInstanceContextValue]): Future[String] = {
    val createInstanceQuery =
      s"""
         |mutation {
         |	createInstance(flowDefinitionId: "$flowDefinitionId", context: [
         |    ${contextValues.map(value => s""" {key: "${value.key}", value : "${value.value}"} """).mkString(",")}
         |  ]) {id}
         |}
       """.stripMargin
    query[CreateInstanceResult[IdResult]](createInstanceQuery).map(_.data.createInstance.id)
  }

  override def deleteInstance(flowInstanceId: String): Future[String] = {
    val deleteInstanceQuery =
      s"""
         |mutation {
         |	deleteInstance(flowInstanceId: "$flowInstanceId")
         |}
     """.stripMargin
    query[DeleteInstanceResult[String]](deleteInstanceQuery).map(_.data.deleteInstance)
  }

  override def setTaskStatus(taskInstanceId: String, status: FlowTaskInstanceStatus, retries: Int): Future[String] = {
    val deleteInstanceQuery =
      s"""
         |mutation {
         |	setTaskStatus(taskInstanceId: "$taskInstanceId", status: "${status.toString}", retries: $retries, nextRetry: 0) { id }
         |}
     """.stripMargin
    query[SetTaskStatusResult[IdResult]](deleteInstanceQuery).map(_.data.setTaskStatus.id)
  }

  override def deleteFlowDefinition(id: String): Future[String] = {
    val deleteFlowDefinitionQuery =
      s"""
         |mutation ($$flowDefinitionId: String!) {
         |	deleteFlowDefinition(flowDefinitionId: $$flowDefinitionId)
         |}
       """.stripMargin

    query[DeleteFlowResult[String]](deleteFlowDefinitionQuery, Map("flowDefinitionId" -> Json.fromString(id))).map(_.data.deleteFlowDefinition)
  }
}
