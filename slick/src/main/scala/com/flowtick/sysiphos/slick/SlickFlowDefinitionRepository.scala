package com.flowtick.sysiphos.slick

import java.time.{ LocalDateTime, ZoneOffset }
import java.util.UUID

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowDefinitionRepository }
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

class SlickFlowDefinitionRepository(dataSource: DataSource)(implicit val profile: JdbcProfile, executionContext: ExecutionContext) extends FlowDefinitionRepository {
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

  override def addFlowDefinition(flowDefinition: FlowDefinition)(implicit repositoryContext: RepositoryContext): Future[FlowDefinition] = {
    val newDefinition = SlickFlowDefinition(
      id = UUID.randomUUID().toString,
      json = FlowDefinition.toJson(flowDefinition),
      version = 0L,
      created = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
      updated = None,
      creator = repositoryContext.currentUser)

    db.run(flowDefinitionTable += newDefinition).map(_ => flowDefinition)
  }

  override def getFlowDefinitions(implicit repositoryContext: RepositoryContext): Future[Seq[FlowDefinition]] =
    db.run(flowDefinitionTable.result.map(definitions => definitions.flatMap(definition => FlowDefinition.fromJson(definition.json).toOption)))
}
