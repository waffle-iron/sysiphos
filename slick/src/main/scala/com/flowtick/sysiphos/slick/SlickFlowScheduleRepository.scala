package com.flowtick.sysiphos.slick

import java.util.UUID

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.scheduler.{ FlowScheduleDetails, FlowScheduleRepository, FlowScheduleStateStore }
import javax.sql.DataSource
import org.slf4j.{ Logger, LoggerFactory }
import slick.jdbc.JdbcProfile

import scala.concurrent.{ ExecutionContext, Future }

class SlickFlowScheduleRepository(dataSource: DataSource)(implicit val profile: JdbcProfile, executionContext: ExecutionContext) extends FlowScheduleRepository
  with FlowScheduleStateStore {

  val log: Logger = LoggerFactory.getLogger(getClass)

  import profile.api._

  val db: profile.backend.DatabaseDef = profile.backend.Database.forDataSource(dataSource, None, AsyncExecutor.default("flow-schedule-repository"))

  class FlowSchedules(tag: Tag) extends Table[FlowScheduleDetails](tag, "_FLOW_SCHEDULE") {
    def id = column[String]("_ID", O.PrimaryKey)
    def creator = column[String]("_CREATOR")
    def created = column[Long]("_CREATED")
    def version = column[Long]("_VERSION")
    def updated = column[Option[Long]]("_UPDATED")
    def expression = column[Option[String]]("_EXPRESSION")
    def flowDefinitionId = column[String]("_FLOW_DEFINITION_ID")
    def flowTaskId = column[Option[String]]("_FLOW_TASK_ID")
    def nextDueDate = column[Option[Long]]("_NEXT_DUE_DATE")
    def enabled = column[Option[Boolean]]("_ENABLED")

    def * = (id, creator, created, version, updated, expression, flowDefinitionId, flowTaskId, nextDueDate, enabled) <> (FlowScheduleDetails.tupled, FlowScheduleDetails.unapply)
  }

  val flowSchedulesTable = TableQuery[FlowSchedules]

  def newId: String = UUID.randomUUID().toString

  override def createFlowSchedule(
    id: Option[String],
    expression: Option[String],
    flowDefinitionId: String,
    flowTaskId: Option[String],
    enabled: Option[Boolean])(implicit repositoryContext: RepositoryContext): Future[FlowScheduleDetails] = {
    val newSchedule = FlowScheduleDetails(
      id = id.getOrElse(newId),
      creator = repositoryContext.currentUser,
      created = repositoryContext.epochSeconds,
      version = 0L,
      updated = None,
      expression = expression,
      flowDefinitionId = flowDefinitionId,
      flowTaskId,
      None,
      enabled = enabled)

    db.run(flowSchedulesTable += newSchedule)
      .filter(_ > 0)
      .map(_ => newSchedule)
  }

  override def getFlowSchedules(onlyEnabled: Boolean)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowScheduleDetails]] = {
    db.run(flowSchedulesTable.filter(schedule => if (onlyEnabled) schedule.enabled === true else schedule.enabled === schedule.enabled).result)
  }

  override def setDueDate(flowScheduleId: String, dueDate: Long)(implicit repositoryContext: RepositoryContext): Future[Unit] = {
    val dueDateUpdate = flowSchedulesTable
      .filter(_.id === flowScheduleId)
      .map(schedule => (schedule.nextDueDate, schedule.updated))
      .update((Some(dueDate), Some(repositoryContext.epochSeconds)))

    db.run(dueDateUpdate)
      .filter(_ > 0)
      .map(_ => ())
  }

  override def updateFlowSchedule(
    id: String,
    expression: Option[String],
    enabled: Option[Boolean])(implicit repositoryContext: RepositoryContext): Future[FlowScheduleDetails] = {
    db.run(flowSchedulesTable.filter(_.id === id).result.headOption).flatMap {
      case Some(existing) =>
        val newExpression = expression.orElse(existing.expression)
        val newEnabled = enabled.orElse(existing.enabled)

        val scheduleUpdate =
          flowSchedulesTable
            .filter(_.id === existing.id)
            .map(flow => (flow.expression, flow.enabled, flow.nextDueDate, flow.version, flow.updated))
            .update((newExpression, newEnabled, existing.nextDueDate, existing.version + 1, Some(repositoryContext.epochSeconds)))

        db.run(scheduleUpdate)
          .filter(_ > 0)
          .map(_ => existing.copy(enabled = newEnabled, expression = newExpression))
      case None => Future.failed(new IllegalArgumentException(s"could not find schedule with id $id"))
    }
  }
}
