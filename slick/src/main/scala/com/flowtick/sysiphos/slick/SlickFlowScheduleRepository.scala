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

  class FlowSchedules(tag: Tag) extends Table[SlickCronSchedule](tag, "_flow_schedule") {
    def id = column[String]("_id", O.PrimaryKey)
    def creator = column[String]("_creator")
    def created = column[Long]("_created")
    def updated = column[Option[Long]]("_updated")
    def expression = column[String]("_expression")
    def flowDefinitionId = column[String]("_flow_definition_id")
    def flowTaskId = column[Option[String]]("_flow_task_id")
    def nextDueDate = column[Option[Long]]("_next_due_date")
    def enabled = column[Option[Boolean]]("_enabled")

    def * = (id, creator, created, updated, expression, flowDefinitionId, flowTaskId, nextDueDate, enabled) <> (SlickCronSchedule.tupled, SlickCronSchedule.unapply)
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
