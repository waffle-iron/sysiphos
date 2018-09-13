package com.flowtick.sysiphos.api

import java.io.{ PrintWriter, StringWriter }

import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.api.resources.{ GraphIQLResources, UIResources }
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.FlowInstanceStatus.FlowInstanceStatus
import com.flowtick.sysiphos.flow.FlowTaskInstanceStatus.FlowTaskInstanceStatus
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler.FlowScheduleDetails
import com.twitter.finagle.http.Status
import io.circe.Json
import sangria.ast.Document
import sangria.execution.{ Executor, ValidationError }
import sangria.macros.derive.GraphQLField
import sangria.marshalling.circe._
import sangria.parser.QueryParser
import sangria.schema._

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
    def schedules(id: Option[String], flowId: Option[String]): Future[Seq[FlowScheduleDetails]]

    @GraphQLField
    def instances(
      flowDefinitionId: Option[String],
      instanceIds: Option[Seq[String]],
      status: Option[String],
      createdGreaterThan: Option[Long]): Future[Seq[FlowInstanceDetails]]

    @GraphQLField
    def taskInstances(flowInstanceId: String): Future[Seq[FlowTaskInstanceDetails]]
  }

  trait ApiMutationContext {
    @GraphQLField
    def createOrUpdateFlowDefinition(json: String): Future[FlowDefinitionDetails]

    @GraphQLField
    def createFlowSchedule(
      id: Option[String],
      flowDefinitionId: String,
      flowTaskId: Option[String],
      expression: Option[String],
      enabled: Option[Boolean]): Future[FlowScheduleDetails]

    @GraphQLField
    def updateFlowSchedule(
      id: String,
      expression: Option[String],
      enabled: Option[Boolean]): Future[FlowScheduleDetails]

    @GraphQLField
    def setDueDate(flowScheduleId: String, dueDate: Long): Future[Boolean]
  }

  trait ApiContext extends ApiQueryContext with ApiMutationContext

  implicit val FlowScheduleType = deriveObjectType[SysiphosApiContext, FlowScheduleDetails](
    ObjectTypeName("FlowSchedule"),
    ObjectTypeDescription("A (time) schedule for a flow"))

  implicit val InstanceCountType = deriveObjectType[SysiphosApiContext, InstanceCount](
    ObjectTypeName("InstanceCount"),
    ObjectTypeDescription("the instance count by status for a flow definition"))

  implicit val FlowDefinitionSummaryType = deriveObjectType[SysiphosApiContext, FlowDefinitionSummary](
    ObjectTypeName("FlowDefinitionSummary"),
    ObjectTypeDescription("the summary for a flow definition"))

  implicit val FlowDefinitionDetailsType = deriveObjectType[SysiphosApiContext, FlowDefinitionDetails](
    ObjectTypeName("FlowDefinitionDetails"),
    ObjectTypeDescription("the details for a flow definition"))

  implicit val FlowInstanceContextValueType = deriveObjectType[SysiphosApiContext, FlowInstanceContextValue](
    ObjectTypeName("FlowInstanceContextValue"),
    ObjectTypeDescription("the value of a context variable"))

  implicit val FlowInstanceStatusType = deriveEnumType[FlowInstanceStatus]()

  implicit val FlowInstanceDetailsType = deriveObjectType[SysiphosApiContext, FlowInstanceDetails](
    ObjectTypeName("FlowInstanceDetails"),
    ObjectTypeDescription("the details for an execution (instance) of a flow definition"))

  implicit val FlowTaskInstanceStatusType = deriveEnumType[FlowTaskInstanceStatus]()

  implicit val FlowTaskInstanceDetailsType = deriveObjectType[SysiphosApiContext, FlowTaskInstanceDetails](
    ObjectTypeName("FlowTaskInstanceDetails"),
    ObjectTypeDescription("the details for an execution (instance) of a task"))

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

  def errorResponse(status: Status, error: Exception): Output[Json] = {
    val sw = new StringWriter()
    error.printStackTrace(new PrintWriter(sw))
    val stackTrace = sw.toString

    Output.payload(Json.obj("error" -> Json.fromString(s"${error.getMessage}, $stackTrace")), status)
  }

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
  }.handle {
    case invalidQuery: ValidationError => errorResponse(Status.BadRequest, invalidQuery)
    case error: Exception => errorResponse(Status.InternalServerError, error)
  }

  def parseQuery(query: String): Try[Document] = QueryParser.parse(query)

  def executeQuery(query: Document, operation: Option[String], vars: Json, apiContext: ApiContext): Future[Json] =
    Executor.execute(SysiphosApi.schema, query, apiContext, variables = vars, operationName = operation)

  val api = statusEndpoint :+: apiEndpoint
}
