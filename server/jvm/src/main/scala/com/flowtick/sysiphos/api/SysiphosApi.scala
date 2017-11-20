package com.flowtick.sysiphos.api

import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.api.resources.{ GraphIQLResources, UIResources }
import com.flowtick.sysiphos.flow.FlowDefinition
import com.flowtick.sysiphos.scheduler.FlowSchedule
import com.twitter.finagle.Service
import com.twitter.finagle.http.{ Request, Response }
import io.circe.Json
import sangria.ast.Document
import sangria.execution.Executor
import sangria.marshalling.circe._
import sangria.parser.QueryParser
import sangria.schema.{ Field, ListType, ObjectType, OptionType, _ }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object SysiphosApi {
  trait ApiContext {
    def findFlowDefinition(id: String): Option[FlowDefinition]
    def findFlowDefinitions(): Seq[FlowDefinition]
    def findSchedule(id: String): Option[FlowSchedule]
    def findSchedules(): Seq[FlowSchedule]
  }

  val FlowScheduleType = ObjectType(
    "FlowSchedule",
    "A schedule for a flow",
    fields[Unit, FlowSchedule](
      Field("id", StringType, resolve = _.value.id)))

  val Id = Argument("id", StringType)

  val QueryType = ObjectType("Query", fields[ApiContext, Unit](
    Field(
      "schedule",
      OptionType(FlowScheduleType),
      description = Some("Returns a product with specific `id`."),
      arguments = Id :: Nil,
      resolve = c => c.ctx.findSchedule(c arg Id)),
    Field(
      "schedules",
      ListType(FlowScheduleType),
      description = Some("Returns a list of all available products."),
      resolve = _.ctx.findSchedules())))

  val schema = Schema(QueryType)
}

trait SysiphosApi extends GraphIQLResources with UIResources {
  import io.finch._
  import io.finch.circe._

  val apiContext: ApiContext
  implicit val executionContext: ExecutionContext

  val statusEndpoint: Endpoint[String] = get("status") { Ok("OK") }

  val apiEndpoint: Endpoint[Json] = post("api" :: jsonBody[Json]) { json: Json =>
    val result: Future[Json] = json.asObject.flatMap { queryObj =>
      val query: Option[String] = queryObj("query").flatMap(_.asString)
      val operationName: Option[String] = queryObj("operationName").flatMap(_.asString)
      val variables: Json = queryObj("variables").filter(!_.isNull).getOrElse(Json.obj())

      query.map(parseQuery).map {
        case Success(document) => executeQuery(document, operationName, variables)
        case Failure(parseError) => Future.failed(parseError)
      }
    }.getOrElse(Future.failed(new IllegalArgumentException("invalid json body")))

    result.map(Ok).asTwitter
  }

  def parseQuery(query: String): Try[Document] = QueryParser.parse(query)

  def executeQuery(query: Document, operation: Option[String], vars: Json): Future[Json] =
    Executor.execute(SysiphosApi.schema, query, apiContext, variables = vars, operationName = operation)

  val api: Service[Request, Response] = (statusEndpoint :+: apiEndpoint :+: graphiqlResources :+: uiResources).toServiceAs[Application.Json]
}
