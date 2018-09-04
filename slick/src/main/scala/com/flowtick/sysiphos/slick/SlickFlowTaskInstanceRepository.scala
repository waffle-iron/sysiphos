package com.flowtick.sysiphos.slick

import java.time.{ LocalDateTime, ZoneOffset }
import java.util.UUID

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow._
import javax.sql.DataSource

import org.slf4j.{ Logger, LoggerFactory }
import slick.jdbc.JdbcProfile

import scala.concurrent.{ ExecutionContext, Future }

final case class SlickFlowTaskInstance(
  id: String,
  flowInstanceId: String,
  taskId: String,
  creationTime: Long,
  updated: Option[Long],
  creator: String,
  status: String,
  retries: Int,
  startTime: Option[Long] = None,
  endTime: Option[Long] = None) extends FlowTaskInstance

class SlickFlowTaskInstanceRepository(dataSource: DataSource)(implicit val profile: JdbcProfile, executionContext: ExecutionContext) extends FlowTaskInstanceRepository[FlowTaskInstance] {
  val log: Logger = LoggerFactory.getLogger(getClass)

  import profile.api._

  val db: profile.backend.DatabaseDef = profile.backend.Database.forDataSource(dataSource, None, AsyncExecutor.default("flow-task-instance-repository"))

  class FlowTaskInstances(tag: Tag) extends Table[SlickFlowTaskInstance](tag, "_FLOW_TASK_INSTANCE") {
    def id = column[String]("_ID", O.PrimaryKey)
    def flowInstanceId = column[String]("_FLOW_INSTANCE_ID")
    def userTaskId = column[String]("_USER_TASK_ID")
    def created = column[Long]("_CREATED")
    def updated = column[Option[Long]]("_UPDATED")
    def creator = column[String]("_CREATOR")
    def status = column[String]("_STATUS")
    def retries = column[Int]("_RETRIES")
    def startTime = column[Option[Long]]("_START_TIME")
    def endTime = column[Option[Long]]("_END_TIME")

    def * = (id, flowInstanceId, userTaskId, created, updated, creator, status, retries, startTime, endTime) <> (SlickFlowTaskInstance.tupled, SlickFlowTaskInstance.unapply)
  }

  case class SysiphosFlowTaskInstanceContext(
    id: String,
    flowTaskInstanceId: String,
    key: String,
    value: String)

  private val instanceTable = TableQuery[FlowTaskInstances]

  private[slick] def getFlowTaskInstances: Future[Seq[SlickFlowTaskInstance]] = db.run(instanceTable.result)

  override def getFlowTaskInstances(flowInstanceId: String)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowTaskInstance]] = {
    val queryBuilder = instanceTable.filter(_.flowInstanceId === flowInstanceId).result

    db.run(queryBuilder)
  }

  override def findById(id: String)(implicit repositoryContext: RepositoryContext): Future[Option[SlickFlowTaskInstance]] = {
    val queryBuilder = instanceTable.filter(_.id === id).result.headOption

    db.run(queryBuilder)
  }

  override def createFlowTaskInstance(flowInstanceId: String, flowTaskId: String)(implicit repositoryContext: RepositoryContext): Future[FlowTaskInstance] = {

    val newInstance = SlickFlowTaskInstance(
      id = UUID.randomUUID().toString,
      flowInstanceId = flowInstanceId,
      taskId = flowTaskId,
      creationTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
      creator = repositoryContext.currentUser,
      updated = None,
      status = "new",
      retries = 0,
      startTime = None,
      endTime = None)

    db.run((instanceTable += newInstance).transactionally).map(_ => newInstance)
  }

  override def update(flowInstance: FlowTaskInstance)(implicit repositoryContext: RepositoryContext): Future[FlowTaskInstance] = {
    val columnsForUpdates = instanceTable.filter(_.id === flowInstance.id)
      .map { task => (task.endTime, task.status, task.retries) }
      .update((flowInstance.endTime, flowInstance.status, flowInstance.retries))

    db.run(columnsForUpdates.transactionally).map(_ => flowInstance)
  }

  override def setStatus(flowInstanceId: String, status: String)(implicit repositoryContext: RepositoryContext): Future[Unit] = {
    val columnsForUpdates = instanceTable.filter(_.id === flowInstanceId)
      .map { task => task.status }
      .update(status)

    db.run(columnsForUpdates.transactionally).filter(_ == 1).map { _ => () }
  }

  override def createFlowTaskInstances(flowInstanceId: String, tasks: Seq[FlowTask])(implicit repositoryContext: RepositoryContext): Future[Seq[FlowTaskInstance]] = {

    val instances = tasks.map { task =>
      SlickFlowTaskInstance(
        id = UUID.randomUUID().toString,
        flowInstanceId = flowInstanceId,
        taskId = task.id,
        creationTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
        creator = repositoryContext.currentUser,
        updated = None,
        status = "new",
        retries = 0,
        startTime = None,
        endTime = None)
    }

    db.run((instanceTable ++= instances).transactionally).map(_ => instances)
  }

  override def setRetries(flowTaskInstanceId: String, retries: Int)(implicit repositoryContext: RepositoryContext): Future[Unit] = {
    val columnsForUpdates = instanceTable.filter(_.id === flowTaskInstanceId)
      .map { task => task.retries }
      .update(retries)

    db.run(columnsForUpdates.transactionally).filter(_ == 1).map { _ => () }
  }
}
