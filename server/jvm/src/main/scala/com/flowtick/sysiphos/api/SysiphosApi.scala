package com.flowtick.sysiphos.api

import java.io.{ PrintWriter, StringWriter }

import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.flow.FlowInstanceStatus.FlowInstanceStatus
import com.flowtick.sysiphos.flow.FlowTaskInstanceStatus.FlowTaskInstanceStatus
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler.FlowScheduleDetails
import com.twitter.finagle.http.Status
import io.circe.Json
import sangria.ast.Document
import sangria.execution._
import sangria.macros.derive.GraphQLField
import sangria.marshalling._
import sangria.marshalling.circe._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory
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
      status: Option[Seq[String]],
      createdGreaterThan: Option[Long],
      createdSmallerThan: Option[Long],
      offset: Option[Int],
      limit: Option[Int]): Future[Seq[FlowInstanceDetails]]

    @GraphQLField
    def contextValues(flowInstanceId: String): Future[Seq[FlowInstanceContextValue]]

    @GraphQLField
    def taskInstances(
      flowInstanceId: Option[String],
      dueBefore: Option[Long],
      status: Option[Seq[String]]): Future[Seq[FlowTaskInstanceDetails]]

    @GraphQLField
    def log(logId: String): Future[String]

    @GraphQLField
    def version: Future[String]

    @GraphQLField
    def name: Future[String]
  }

  trait ApiMutationContext {
    @GraphQLField
    def createOrUpdateFlowDefinition(json: String): Future[FlowDefinitionDetails]

    @GraphQLField
    def deleteFlowDefinition(flowDefinitionId: String): Future[String]

    @GraphQLField
    def createFlowSchedule(
      id: Option[String],
      flowDefinitionId: String,
      flowTaskId: Option[String],
      expression: Option[String],
      enabled: Option[Boolean],
      backFill: Option[Boolean]): Future[FlowScheduleDetails]

    @GraphQLField
    def updateFlowSchedule(
      id: String,
      expression: Option[String],
      enabled: Option[Boolean],
      backFill: Option[Boolean]): Future[FlowScheduleDetails]

    @GraphQLField
    def createInstance(flowDefinitionId: String, context: Seq[FlowInstanceContextValue]): Future[FlowInstanceContext]

    @GraphQLField
    def deleteInstance(flowInstanceId: String): Future[String]

    @GraphQLField
    def setDueDate(flowScheduleId: String, dueDate: Long): Future[Boolean]

    @GraphQLField
    def setTaskStatus(taskInstanceId: String, status: String, retries: Option[Int], nextRetry: Option[Long]): Future[Option[FlowTaskInstanceDetails]]
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

  implicit val FlowInstanceContextValueInputType = deriveInputObjectType[FlowInstanceContextValue](
    InputObjectTypeName("FlowInstanceContextValueInput"))

  implicit val FlowInstanceStatusType = deriveEnumType[FlowInstanceStatus]()

  implicit val FlowInstanceDetailsType = deriveObjectType[SysiphosApiContext, FlowInstanceDetails](
    ObjectTypeName("FlowInstanceDetails"),
    ObjectTypeDescription("the details for an execution (instance) of a flow definition"))

  implicit val FlowTaskInstanceStatusType = deriveEnumType[FlowTaskInstanceStatus]()

  implicit val FlowTaskInstanceDetailsType = deriveObjectType[SysiphosApiContext, FlowTaskInstanceDetails](
    ObjectTypeName("FlowTaskInstanceDetails"),
    ObjectTypeDescription("the details for an execution (instance) of a task"))

  implicit val FlowInstanceContextType = deriveObjectType[SysiphosApiContext, FlowInstanceContext](
    ObjectTypeName("FlowInstanceContext"),
    ObjectTypeDescription("the context of a flow instance"))

  val MutationType = deriveContextObjectType[ApiContext, ApiMutationContext, Unit](identity)
  val QueryType = deriveContextObjectType[ApiContext, ApiQueryContext, Unit](identity)

  val schema = Schema(QueryType, Some(MutationType))
}

trait SysiphosApi {
  import io.finch._
  import io.finch.circe._
  import io.finch.syntax._

  private def stackTrace(throwable: Throwable): String = {
    val sw = new StringWriter()
    throwable.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  def errorResponse(status: Status, error: Exception): Output[Json] = {
    val errorJson = Json.obj("message" -> Json.fromString(s"${error.getMessage}, ${stackTrace(error)}"))
    Output.payload(Json.obj("errors" -> Json.fromValues(Seq(errorJson))), status) // graphql like error format
  }

  def apiEndpoint(apiContext: ApiContext)(implicit executionContext: ExecutionContext): Endpoint[Json] = post("api" :: jsonBody[Json]) { json: Json =>
    val result: Future[Output[Json]] = json.asObject.flatMap { queryObj =>
      val query: Option[String] = queryObj("query").flatMap(_.asString)
      val operationName: Option[String] = queryObj("operationName").flatMap(_.asString)
      val variables: Json = queryObj("variables").filter(!_.isNull).getOrElse(Json.obj())

      query.map(parseQuery).map {
        case Success(document) => executeQuery(document, operationName, variables, apiContext)
        case Failure(parseError) => Future.failed(parseError)
      }
    }.getOrElse(Future.failed(new IllegalArgumentException("invalid json body")))

    result.asTwitter
  }.handle {
    case invalidQuery: ValidationError => errorResponse(Status.BadRequest, invalidQuery)
    case error: Exception =>
      LoggerFactory.getLogger(getClass).error("unknown error", error)
      errorResponse(Status.InternalServerError, error)
  }

  def parseQuery(query: String): Try[Document] = QueryParser.parse(query)

  def executeQuery(
    query: Document,
    operation: Option[String],
    vars: Json,
    apiContext: ApiContext)(implicit executionContext: ExecutionContext): Future[Output[Json]] = {
    val executedQuery = Executor.execute(
      SysiphosApi.schema,
      query,
      apiContext,
      variables = vars,
      operationName = operation,
      exceptionHandler = ExceptionHandler(onException = {
        case (_, error) => SingleHandledException(stackTrace(error))
      }))

    executedQuery.map { json =>
      json.hcursor.downField("data").focus match {
        case None | Some(io.circe.Json.Null) => Output.payload(json, Status.InternalServerError)
        case _ => Output.payload(json, Status.Ok)
      }
    }.recover {
      case error: QueryAnalysisError => Output.payload(error.resolveError, Status.BadRequest)
      case error: ErrorWithResolver => Output.payload(error.resolveError, Status.InternalServerError)
    }
  }

  def api(context: ApiContext)(implicit executionContext: ExecutionContext): Endpoint[Json] = apiEndpoint(context)
}
