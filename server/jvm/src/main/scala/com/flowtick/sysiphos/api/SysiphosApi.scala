package com.flowtick.sysiphos.api

import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.api.resources.{ GraphIQLResources, UIResources }
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowDefinitionDetails, FlowDefinitionSummary, InstanceCount }
import com.flowtick.sysiphos.scheduler.FlowSchedule
import io.circe.Json
import sangria.ast.Document
import sangria.execution.Executor
import sangria.macros.derive.GraphQLField
import sangria.marshalling.circe._
import sangria.parser.QueryParser
import sangria.schema.{ Field, ObjectType, _ }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object SysiphosApi {
  import sangria.macros.derive._

  trait ApiQueryContext {
    @GraphQLField
    def definitions(id: Option[String]): Future[Seq[FlowDefinitionSummary]]

    @GraphQLField
    def definition(id: String): Future[Option[FlowDefinitionDetails]]

    @GraphQLField
    def schedules(id: Option[String]): Future[Seq[FlowSchedule]]
  }

  trait ApiMutationContext {
    @GraphQLField
    def foo = "bar"
  }

  trait ApiContext extends ApiQueryContext with ApiMutationContext

  implicit val FlowDefinitionType = ObjectType(
    "FlowDefinition",
    "A flow definition",
    fields[Unit, FlowDefinition](
      Field("id", StringType, resolve = _.value.id)))

  implicit val FlowScheduleType = ObjectType(
    "FlowSchedule",
    "A schedule for a flow",
    fields[Unit, FlowSchedule](
      Field("id", StringType, resolve = _.value.id)))

  implicit val InstanceCountType = deriveObjectType[SysiphosApiContext, InstanceCount](
    ObjectTypeName("InstanceCount"),
    ObjectTypeDescription("the instance count by status for a flow definition"))

  implicit val FlowDefinitionSummaryType = deriveObjectType[SysiphosApiContext, FlowDefinitionSummary](
    ObjectTypeName("FlowDefinitionSummary"),
    ObjectTypeDescription("the summary for a flow definition"))

  implicit val FlowDefinitionDetailsType = deriveObjectType[SysiphosApiContext, FlowDefinitionDetails](
    ObjectTypeName("FlowDefinitionDetails"),
    ObjectTypeDescription("the details for a flow definition"))

  val MutationType = deriveContextObjectType[ApiContext, ApiMutationContext, Unit](identity)
  val QueryType = deriveContextObjectType[ApiContext, ApiQueryContext, Unit](identity)

  val schema = Schema(QueryType, Some(MutationType))
}

trait SysiphosApi extends GraphIQLResources with UIResources {
  import io.finch._
  import io.finch.circe._

  def apiContext(repositoryContext: RepositoryContext): ApiContext
  implicit val executionContext: ExecutionContext

  val statusEndpoint: Endpoint[String] = get("status") { Ok("OK") }

  val apiEndpoint: Endpoint[Json] = post("api" :: jsonBody[Json]) { json: Json =>
    val result: Future[Json] = json.asObject.flatMap { queryObj =>
      val query: Option[String] = queryObj("query").flatMap(_.asString)
      val operationName: Option[String] = queryObj("operationName").flatMap(_.asString)
      val variables: Json = queryObj("variables").filter(!_.isNull).getOrElse(Json.obj())

      query.map(parseQuery).map {
        case Success(document) => executeQuery(document, operationName, variables, apiContext(new RepositoryContext {
          override def currentUser: String = "user"
        }))
        case Failure(parseError) => Future.failed(parseError)
      }
    }.getOrElse(Future.failed(new IllegalArgumentException("invalid json body")))

    result.map(Ok).asTwitter
  }

  def parseQuery(query: String): Try[Document] = QueryParser.parse(query)

  def executeQuery(query: Document, operation: Option[String], vars: Json, apiContext: ApiContext): Future[Json] =
    Executor.execute(SysiphosApi.schema, query, apiContext, variables = vars, operationName = operation)

  val api = statusEndpoint :+: apiEndpoint
}
