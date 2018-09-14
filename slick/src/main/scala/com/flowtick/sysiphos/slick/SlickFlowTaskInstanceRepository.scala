package com.flowtick.sysiphos.slick

import java.util.UUID

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow._
import javax.sql.DataSource
import org.slf4j.{ Logger, LoggerFactory }
import slick.jdbc.JdbcProfile

import scala.concurrent.{ ExecutionContext, Future }

class SlickFlowTaskInstanceRepository(dataSource: DataSource)(implicit val profile: JdbcProfile, executionContext: ExecutionContext) extends FlowTaskInstanceRepository {
  val log: Logger = LoggerFactory.getLogger(getClass)

  import profile.api._

  val db: profile.backend.DatabaseDef = profile.backend.Database.forDataSource(dataSource, None, AsyncExecutor.default("flow-task-instance-repository"))

  class FlowTaskInstances(tag: Tag) extends Table[FlowTaskInstanceDetails](tag, "_FLOW_TASK_INSTANCE") {
    def id = column[String]("_ID", O.PrimaryKey)
    def flowInstanceId = column[String]("_FLOW_INSTANCE_ID")
    def taskId = column[String]("_TASK_ID")
    def created = column[Long]("_CREATED")
    def updated = column[Option[Long]]("_UPDATED")
    def status = column[String]("_STATUS")
    def retries = column[Int]("_RETRIES")
    def retryDelay = column[Option[Long]]("_RETRY_DELAY")
    def nextDueDate = column[Option[Long]]("_NEXT_DUE_DATE")
    def startTime = column[Option[Long]]("_START_TIME")
    def endTime = column[Option[Long]]("_END_TIME")
    def logId = column[Option[String]]("_LOG_ID")

    def fromTuple(tuple: (String, String, String, Long, Option[Long], Option[Long], Option[Long], Int, String, Option[Long], Option[Long], Option[String])): FlowTaskInstanceDetails = tuple match {
      case (id, flowInstanceId, taskId, created, updated, startTime, endTime, retries, status, retryDelay, nextDueDate, logId) =>
        FlowTaskInstanceDetails(
          id, flowInstanceId, taskId, created, updated, startTime, endTime, retries, FlowTaskInstanceStatus.withName(status), retryDelay, nextDueDate, logId)
    }

    def toTuple(instance: FlowTaskInstanceDetails): Option[(String, String, String, Long, Option[Long], Option[Long], Option[Long], Int, String, Option[Long], Option[Long], Option[String])] = Some((
      instance.id,
      instance.flowInstanceId,
      instance.taskId,
      instance.creationTime,
      instance.updatedTime,
      instance.startTime,
      instance.endTime,
      instance.retries,
      instance.status.toString,
      instance.retryDelay,
      instance.nextDueDate,
      instance.logId))

    def * = (id, flowInstanceId, taskId, created, updated, startTime, endTime, retries, status, retryDelay, nextDueDate, logId) <> (
      fromTuple,
      toTuple)
  }

  case class SysiphosFlowTaskInstanceContext(
    id: String,
    flowTaskInstanceId: String,
    key: String,
    value: String)

  private val taskInstancesTable = TableQuery[FlowTaskInstances]

  private[slick] def getFlowTaskInstances: Future[Seq[FlowTaskInstanceDetails]] = db.run(taskInstancesTable.result)

  override def getFlowTaskInstances(flowInstanceId: String)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowTaskInstanceDetails]] = {
    val queryBuilder = taskInstancesTable.filter(_.flowInstanceId === flowInstanceId).result

    db.run(queryBuilder)
  }

  override def findById(id: String)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]] = {
    val queryBuilder = taskInstancesTable.filter(_.id === id).result.headOption

    db.run(queryBuilder)
  }

  protected def newId: String = UUID.randomUUID().toString

  override def createFlowTaskInstance(instanceId: String, flowTaskId: String)(implicit repositoryContext: RepositoryContext): Future[FlowTaskInstanceDetails] = {

    val newInstance = FlowTaskInstanceDetails(
      id = newId,
      flowInstanceId = instanceId,
      taskId = flowTaskId,
      creationTime = repositoryContext.epochSeconds,
      startTime = None,
      status = FlowTaskInstanceStatus.New,
      retries = 3,
      endTime = None,
      retryDelay = Some(10L),
      nextDueDate = None,
      logId = None)

    db.run((taskInstancesTable += newInstance).transactionally).map(_ => newInstance)
  }

  override def setStatus(taskInstanceId: String, status: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]] = {
    val columnsForUpdates = taskInstancesTable.filter(_.id === taskInstanceId)
      .map { task => task.status }
      .update(status.toString)

    db.run(columnsForUpdates.transactionally).filter(_ == 1).flatMap { _ => findById(taskInstanceId) }
  }

  override def setRetries(taskInstanceId: String, retries: Int)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]] = {
    val columnsForUpdates = taskInstancesTable.filter(_.id === taskInstanceId)
      .map { task => task.retries }
      .update(retries)

    db.run(columnsForUpdates.transactionally).filter(_ == 1).flatMap(_ => findById(taskInstanceId))
  }

  override def setNextDueDate(taskInstanceId: String, nextDueDate: Option[Long])(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]] = {
    val columnsForUpdates = taskInstancesTable.filter(_.id === taskInstanceId)
      .map { task => task.nextDueDate }
      .update(nextDueDate)

    db.run(columnsForUpdates.transactionally).filter(_ == 1).flatMap(_ => findById(taskInstanceId))
  }

  override def getScheduled()(implicit repositoryContext: RepositoryContext): Future[Seq[FlowTaskInstanceDetails]] = {
    val queryBuilder = taskInstancesTable.filter(_.nextDueDate.isDefined).result

    db.run(queryBuilder)
  }

  override def setLogId(taskInstanceId: String, logId: String)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]] = {
    val updateLogId = taskInstancesTable.filter(_.id === taskInstanceId)
      .map { task => task.logId }
      .update(Some(logId))

    db.run(updateLogId).filter(_ == 1).flatMap(_ => findById(taskInstanceId))
  }
}
