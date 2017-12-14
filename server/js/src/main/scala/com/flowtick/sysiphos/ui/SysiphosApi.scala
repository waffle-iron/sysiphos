package com.flowtick.sysiphos.ui

import io.circe
import io.circe.{ Decoder, Json }
import io.circe.generic.auto._
import io.circe.parser._
import org.scalajs.dom.ext.Ajax

import scala.concurrent.{ ExecutionContext, Future }

case class FlowDefinitionSummary(id: String)
case class FlowDefinitionList(definitions: Seq[FlowDefinitionSummary])
case class FlowDefinitionResponse(data: FlowDefinitionList)

trait SysiphosApi {
  def getFlowDefinitions: Future[Either[circe.Error, FlowDefinitionResponse]]
}

class SysiphosApiClient(implicit executionContext: ExecutionContext) extends SysiphosApi {
  def graphQlQuery[T](query: String, variables: Map[String, Json] = Map.empty)(implicit ev: Decoder[T]) = {
    val queryJson = Json.obj(
      "query" -> Json.fromString(query),
      "variables" -> Json.fromFields(variables)).noSpaces

    Ajax.post("/api", queryJson).map(response => decode[T](response.responseText))
  }

  override def getFlowDefinitions: Future[Either[circe.Error, FlowDefinitionResponse]] =
    graphQlQuery[FlowDefinitionResponse]("query { definitions { id } }")
}
