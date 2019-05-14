package com.flowtick.sysiphos.slick

import java.util.UUID

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow._
import javax.sql.DataSource
import org.slf4j.{ Logger, LoggerFactory }
import slick.jdbc.JdbcProfile

import scala.concurrent.{ ExecutionContext, Future }

class SlickFlowTaskInstanceRepository(dataSource: DataSource)(implicit val profile: JdbcProfile, executionContext: ExecutionContext)
  extends FlowTaskInstanceRepository with SlickRepositoryBase {
  val log: Logger = LoggerFactory.getLogger(getClass)

  import profile.api._

  val db: profile.backend.DatabaseDef = profile.backend.Database.forDataSource(dataSource, None, executor("flow-task-instance-repository"))

  class FlowTaskInstances(tag: Tag) extends Table[FlowTaskInstanceDetails](tag, "_FLOW_TASK_INSTANCE") {
    def id = column[String]("_ID", O.PrimaryKey)
    def flowInstanceId = column[String]("_FLOW_INSTANCE_ID")
    def flowDefinitionId = column[String]("_FLOW_DEFINITION_ID")
    def taskId = column[String]("_TASK_ID")
    def created = column[Long]("_CREATED")
    def onFailureTaskId = column[Option[String]]("_ON_FAILURE_TASK_ID")
    def updated = column[Option[Long]]("_UPDATED")
    def status = column[String]("_STATUS")
    def retries = column[Int]("_RETRIES")
    def retryDelay = column[Long]("_RETRY_DELAY")
    def nextDueDate = column[Option[Long]]("_NEXT_DUE_DATE")
    def startTime = column[Option[Long]]("_START_TIME")
    def endTime = column[Option[Long]]("_END_TIME")
    def logId = column[String]("_LOG_ID")

    def fromTuple(tuple: (String, String, String, String, Long, Option[String], Option[Long], Option[Long], Option[Long], Int, String, Long, Option[Long], String)): FlowTaskInstanceDetails = tuple match {
      case (id, flowInstanceId, flowDefinitionId, taskId, created, onFailureTaskId, updated, startTime, endTime, retries, status, retryDelay, nextDueDate, logId) =>
        FlowTaskInstanceDetails(
          id, flowInstanceId, flowDefinitionId, taskId, created, onFailureTaskId, updated, startTime, endTime, retries, FlowTaskInstanceStatus.withName(status), retryDelay, nextDueDate, logId)
    }

    def toTuple(instance: FlowTaskInstanceDetails): Option[(String, String, String, String, Long, Option[String], Option[Long], Option[Long], Option[Long], Int, String, Long, Option[Long], String)] = Some((
      instance.id,
      instance.flowInstanceId,
      instance.flowDefinitionId,
      instance.taskId,
      instance.creationTime,
      instance.onFailureTaskId,
      instance.updatedTime,
      instance.startTime,
      instance.endTime,
      instance.retries,
      instance.status.toString,
      instance.retryDelay,
      instance.nextDueDate,
      instance.logId))

    def * = (id, flowInstanceId, flowDefinitionId, taskId, created, onFailureTaskId, updated, startTime, endTime, retries, status, retryDelay, nextDueDate, logId) <> (
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

  private[slick] def createQuery(taskInstanceQuery: FlowTaskInstanceQuery) = {
    val filtered = taskInstancesTable
      .filterOptional(taskInstanceQuery.id)(id => _.id === id)
      .filterOptional(taskInstanceQuery.flowInstanceId)(flowInstanceId => _.flowInstanceId === flowInstanceId)
      .filterOptional(taskInstanceQuery.taskId)(taskId => _.taskId === taskId)
      .filterOptional(taskInstanceQuery.dueBefore)(dueBefore => _.nextDueDate < dueBefore)
      .filterOptional(taskInstanceQuery.status)(status => _.status.inSet(status.map(_.toString)))
      .sortBy(taskInstance => (taskInstance.created.asc, taskInstance.id, taskInstance.retries.desc))

    taskInstanceQuery.limit.map(max => filtered.take(max)).getOrElse(filtered)
  }

  override def find(taskInstanceQuery: FlowTaskInstanceQuery)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowTaskInstanceDetails]] = {
    db.run(createQuery(taskInstanceQuery).result)
  }

  override def findOne(taskInstanceQuery: FlowTaskInstanceQuery)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]] = {
    val queryBuilder = createQuery(taskInstanceQuery).result

    db.run(queryBuilder)
      .filter(_.length <= 1)
      .map(_.headOption)
      .recoverWith {
        case error =>
          Future.failed(new IllegalStateException(s"unable to find single result for $taskInstanceQuery", error))
      }
  }

  protected def newId: String = UUID.randomUUID().toString

  override def createFlowTaskInstance(
    flowInstanceId: String,
    flowTaskId: String,
    flowDefinitionId: String,
    logId: String,
    retries: Int,
    retryDelay: Long,
    dueDate: Option[Long],
    onFailureTaskId: Option[String],
    initialStatus: Option[FlowTaskInstanceStatus.FlowTaskInstanceStatus])(implicit repositoryContext: RepositoryContext): Future[FlowTaskInstanceDetails] = {

    val newInstance = FlowTaskInstanceDetails(
      id = newId,
      flowInstanceId = flowInstanceId,
      flowDefinitionId = flowDefinitionId,
      taskId = flowTaskId,
      creationTime = repositoryContext.epochSeconds,
      onFailureTaskId = onFailureTaskId,
      startTime = None,
      status = initialStatus.getOrElse(FlowTaskInstanceStatus.New),
      retries = retries,
      endTime = None,
      retryDelay = retryDelay,
      nextDueDate = dueDate,
      logId = logId)

    db.run((taskInstancesTable += newInstance).transactionally).map(_ => newInstance)
  }

  override def setStatus(taskInstanceId: String, status: FlowTaskInstanceStatus.FlowTaskInstanceStatus, retries: Option[Int], nextRetry: Option[Long])(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]] = {
    val taskInstance = taskInstancesTable.filter(_.id === taskInstanceId)

    val updates = Seq(
      Some(taskInstance.map { task => task.status }.update(status.toString)),
      retries.map(newRetries => taskInstance.map { task => task.retries }.update(newRetries)),
      nextRetry.map(newNextRetry => taskInstance.map { task => task.nextDueDate }.update(Some(newNextRetry)))).flatten

    db.run(DBIO.seq(updates: _*).transactionally).flatMap { _ => findOne(FlowTaskInstanceQuery(id = Some(taskInstanceId))) }
  }

  override def update(
    query: FlowTaskInstanceQuery,
    status: FlowTaskInstanceStatus.FlowTaskInstanceStatus,
    retries: Option[Int],
    nextRetry: Option[Long])(implicit repositoryContext: RepositoryContext): Future[Unit] = {
    val taskInstance = createQuery(query)

    val updates = Seq(
      Some(taskInstance.map { task => task.status }.update(status.toString)),
      retries.map(newRetries => taskInstance.map { task => task.retries }.update(newRetries)),
      nextRetry.map(newNextRetry => taskInstance.map { task => task.nextDueDate }.update(Some(newNextRetry)))).flatten

    db.run(DBIO.seq(updates: _*).transactionally)
  }

  override def setStartTime(taskInstanceId: String, startTime: Long)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]] = {
    val columnsForUpdates = taskInstancesTable.filter(_.id === taskInstanceId)
      .map { task => task.startTime }
      .update(Some(startTime))

    db.run(columnsForUpdates.transactionally).filter(_ == 1).flatMap { _ => findOne(FlowTaskInstanceQuery(id = Some(taskInstanceId))) }
  }

  override def setEndTime(taskInstanceId: String, endTime: Long)(implicit repositoryContext: RepositoryContext): Future[Option[FlowTaskInstanceDetails]] = {
    val columnsForUpdates = taskInstancesTable.filter(_.id === taskInstanceId)
      .map { task => task.endTime }
      .update(Some(endTime))

    db.run(columnsForUpdates.transactionally).filter(_ == 1).flatMap { _ => findOne(FlowTaskInstanceQuery(id = Some(taskInstanceId))) }
  }

  override def deleteFlowTaskInstance(flowTaskInstanceId: String)(implicit repositoryContext: RepositoryContext): Future[String] = {
    val delete = taskInstancesTable.filter(_.id === flowTaskInstanceId).delete
    db.run(delete).flatMap(_ => Future.successful(flowTaskInstanceId))
  }
}
