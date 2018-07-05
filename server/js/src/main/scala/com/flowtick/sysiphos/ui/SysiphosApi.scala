package com.flowtick.sysiphos.ui

import com.flowtick.sysiphos.flow.{ FlowDefinitionDetails, FlowDefinitionSummary }
import io.circe.{ Decoder, Json }
import io.circe.generic.auto._
import io.circe.parser._
import org.scalajs.dom.ext.Ajax

import scala.concurrent.{ ExecutionContext, Future }

case class FlowDefinitionDetailsResult(definition: Option[FlowDefinitionDetails])
case class FlowDefinitionList(definitions: Seq[FlowDefinitionSummary])
case class GraphQLResponse[T](data: T)

trait SysiphosApi {
  def getFlowDefinitions: Future[GraphQLResponse[FlowDefinitionList]]
  def getFlowDefinition(id: String): Future[Option[FlowDefinitionDetails]]
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
    })
  }

  override def getFlowDefinitions: Future[GraphQLResponse[FlowDefinitionList]] =
    query[FlowDefinitionList]("{ definitions {id, counts { status, count, flowDefinitionId } } }")

  override def getFlowDefinition(id: String): Future[Option[FlowDefinitionDetails]] = {
    query[FlowDefinitionDetailsResult](s"""{ definition(id: "$id") {id, version, source, created} }""").map(_.data.definition)
  }
}
