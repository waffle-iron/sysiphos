package com.flowtick.sysiphos.slick

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowDefinitionDetails, FlowDefinitionRepository }
import javax.sql.DataSource
import org.slf4j.{ Logger, LoggerFactory }
import slick.jdbc.JdbcProfile

import scala.concurrent.{ ExecutionContext, Future }

case class SlickFlowDefinition(
  id: String,
  json: String,
  version: Long,
  created: Long,
  updated: Option[Long],
  creator: String)

class SlickFlowDefinitionRepository(dataSource: DataSource)(implicit val profile: JdbcProfile, executionContext: ExecutionContext) extends FlowDefinitionRepository with SlickRepositoryBase {
  val log: Logger = LoggerFactory.getLogger(getClass)

  import profile.api._

  val db: profile.backend.DatabaseDef = profile.backend.Database.forDataSource(dataSource, None, AsyncExecutor.default("flow-definition-repository"))

  class FlowDefinitions(tag: Tag) extends Table[SlickFlowDefinition](tag, "_FLOW_DEFINITION") {
    def id = column[String]("_ID", O.PrimaryKey)
    def json = column[String]("_JSON")
    def version = column[Long]("_VERSION")
    def created = column[Long]("_CREATED")
    def updated = column[Option[Long]]("_UPDATED")
    def creator = column[String]("_CREATOR")

    def * = (id, json, version, created, updated, creator) <> (SlickFlowDefinition.tupled, SlickFlowDefinition.unapply)
  }

  private val flowDefinitionTable = TableQuery[FlowDefinitions]

  override def createOrUpdateFlowDefinition(flowDefinition: FlowDefinition)(implicit repositoryContext: RepositoryContext): Future[FlowDefinitionDetails] = {
    val slickDefinition = SlickFlowDefinition(
      id = flowDefinition.id,
      json = FlowDefinition.toJson(flowDefinition),
      version = 0L,
      created = repositoryContext.epochSeconds,
      updated = None,
      creator = repositoryContext.currentUser)

    val findExisting = flowDefinitionTable.filter(_.id === flowDefinition.id).result.headOption

    db.run(findExisting).flatMap {
      case None => db.run(flowDefinitionTable += slickDefinition)
      case Some(existing) =>
        val updated = flowDefinitionTable
          .filter(_.id === flowDefinition.id)
          .map(flow => (flow.json, flow.updated, flow.version))
          .update((FlowDefinition.toJson(flowDefinition), Some(repositoryContext.epochSeconds), existing.version + 1))
        db.run(updated)
    }.map(_ => FlowDefinitionDetails(
      flowDefinition.id,
      version = Some(slickDefinition.version),
      source = Some(slickDefinition.json),
      created = Some(slickDefinition.created)))
  }

  def definitionDetails(definition: SlickFlowDefinition): Option[FlowDefinitionDetails] =
    FlowDefinition.fromJson(definition.json).toOption match {
      case Some(parsedDefinition) => Some(FlowDefinitionDetails(
        parsedDefinition.id,
        version = Some(definition.version),
        source = Some(definition.json),
        created = Some(definition.created)))
      case None => None
    }

  override def getFlowDefinitions(implicit repositoryContext: RepositoryContext): Future[Seq[FlowDefinitionDetails]] =
    db.run(flowDefinitionTable.sortBy(_.id).result.map(definitions => definitions.flatMap(definitionDetails)))

  override def findById(id: String)(implicit repositoryContext: RepositoryContext): Future[Option[FlowDefinitionDetails]] = {
    db.run(flowDefinitionTable.filter(_.id === id).result.map(_.headOption.flatMap(definitionDetails)))
  }
}
