package com.flowtick.sysiphos.slick

import java.time.{ LocalDateTime, ZoneOffset }

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.scheduler.{ CronSchedule, FlowScheduleRepository, FlowScheduleStateStore }
import javax.sql.DataSource
import org.slf4j.{ Logger, LoggerFactory }
import slick.jdbc.JdbcProfile

import scala.concurrent.{ ExecutionContext, Future }

final case class SlickCronSchedule(
  id: String,
  creator: String,
  created: Long,
  version: Long,
  updated: Option[Long],
  expression: String,
  flowDefinitionId: String,
  flowTaskId: Option[String],
  nextDueDate: Option[Long],
  enabled: Option[Boolean]) extends CronSchedule

class SlickFlowScheduleRepository(dataSource: DataSource)(implicit val profile: JdbcProfile, executionContext: ExecutionContext) extends FlowScheduleRepository[SlickCronSchedule]
  with FlowScheduleStateStore {

  val log: Logger = LoggerFactory.getLogger(getClass)

  import profile.api._

  val db: profile.backend.DatabaseDef = profile.backend.Database.forDataSource(dataSource, None, AsyncExecutor.default("flow-schedule-repository"))

  class FlowSchedules(tag: Tag) extends Table[SlickCronSchedule](tag, "_FLOW_SCHEDULE") {
    def id = column[String]("_ID", O.PrimaryKey)
    def creator = column[String]("_CREATOR")
    def created = column[Long]("_CREATED")
    def version = column[Long]("_VERSION")
    def updated = column[Option[Long]]("_UPDATED")
    def expression = column[String]("_EXPRESSION")
    def flowDefinitionId = column[String]("_FLOW_DEFINITION_ID")
    def flowTaskId = column[Option[String]]("_FLOW_TASK_ID")
    def nextDueDate = column[Option[Long]]("_NEXT_DUE_DATE")
    def enabled = column[Option[Boolean]]("_ENABLED")

    def * = (id, creator, created, version, updated, expression, flowDefinitionId, flowTaskId, nextDueDate, enabled) <> (SlickCronSchedule.tupled, SlickCronSchedule.unapply)
  }

  val flowSchedulesTable = TableQuery[FlowSchedules]

  override def addFlowSchedule(
    id: String,
    expression: String,
    flowDefinitionId: String,
    flowTaskId: Option[String],
    nextDueDate: Option[Long],
    enabled: Option[Boolean])(implicit repositoryContext: RepositoryContext): Future[SlickCronSchedule] = {
    val newSchedule = SlickCronSchedule(
      id = id,
      creator = repositoryContext.currentUser,
      created = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
      version = 0L,
      updated = None,
      expression = expression,
      flowDefinitionId = flowDefinitionId,
      flowTaskId,
      nextDueDate,
      enabled = enabled)

    db.run(flowSchedulesTable += newSchedule)
      .filter(_ > 0)
      .map(_ => newSchedule)
  }

  override def getFlowSchedules()(implicit repositoryContext: RepositoryContext): Future[Seq[SlickCronSchedule]] = {
    db.run(flowSchedulesTable.result)
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
}
