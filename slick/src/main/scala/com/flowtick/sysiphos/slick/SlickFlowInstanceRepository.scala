package com.flowtick.sysiphos.slick

import java.time.{ LocalDateTime, ZoneOffset }
import java.util.UUID

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowInstance, FlowInstanceQuery, FlowInstanceRepository, InstanceCount }
import javax.sql.DataSource
import org.slf4j.{ Logger, LoggerFactory }
import slick.dbio.Effect.Read
import slick.jdbc.JdbcProfile

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }

final case class SlickFlowInstance(
  id: String,
  flowDefinitionId: String,
  created: Long,
  version: Long,
  updated: Option[Long],
  creator: String,
  status: String,
  retries: Int,
  startTime: Option[Long] = None,
  endTime: Option[Long] = None)

class SlickFlowInstanceRepository(dataSource: DataSource)(implicit val profile: JdbcProfile, executionContext: ExecutionContext) extends FlowInstanceRepository[FlowInstance] {
  val log: Logger = LoggerFactory.getLogger(getClass)

  import profile.api._

  val db: profile.backend.DatabaseDef = profile.backend.Database.forDataSource(dataSource, None, AsyncExecutor.default("flow-instance-repository"))

  class FlowInstances(tag: Tag) extends Table[SlickFlowInstance](tag, "_FLOW_INSTANCE") {
    def id = column[String]("_ID", O.PrimaryKey)
    def flowDefinitionId = column[String]("_FLOW_DEFINITION_ID")
    def created = column[Long]("_CREATED")
    def version = column[Long]("_VERSION")
    def updated = column[Option[Long]]("_UPDATED")
    def creator = column[String]("_CREATOR")
    def status = column[String]("_STATUS")
    def retries = column[Int]("_RETRIES")
    def startTime = column[Option[Long]]("_START_TIME")
    def endTime = column[Option[Long]]("_END_TIME")

    def * = (id, flowDefinitionId, created, version, updated, creator, status, retries, startTime, endTime) <> (SlickFlowInstance.tupled, SlickFlowInstance.unapply)
  }

  case class SysiphosFlowInstanceContext(
    id: String,
    flowInstanceId: String,
    key: String,
    value: String)

  case class InstanceWithContext(instance: SlickFlowInstance, context: Map[String, String]) extends FlowInstance {
    override def retries: Int = instance.retries

    override def status: String = instance.status

    override def id: String = instance.id

    override def flowDefinitionId: String = instance.flowDefinitionId

    override def creationTime: Long = instance.created

    override def startTime: Option[Long] = instance.startTime

    override def endTime: Option[Long] = instance.endTime
  }

  class FlowInstanceContexts(tag: Tag) extends Table[SysiphosFlowInstanceContext](tag, "_FLOW_INSTANCE_CONTEXT") {
    def id = column[String]("_ID", O.PrimaryKey)
    def flowInstanceId = column[String]("_FLOW_INSTANCE_ID")
    def key = column[String]("_KEY")
    def value = column[String]("_VALUE")

    def * = (id, flowInstanceId, key, value) <> (SysiphosFlowInstanceContext.tupled, SysiphosFlowInstanceContext.unapply)
  }

  private val instanceTable = TableQuery[FlowInstances]
  private val contextTable = TableQuery[FlowInstanceContexts]

  private[slick] def getFlowInstances: Future[Seq[SlickFlowInstance]] = db.run(instanceTable.result)

  override def getFlowInstances(query: FlowInstanceQuery)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowInstance]] = {
    val instancesWithContext = (for {
      (instance, context) <- instanceTable.filter(_.flowDefinitionId === query.flowDefinitionId) joinLeft contextTable on (_.id === _.flowInstanceId)
    } yield (instance, context)).result

    db.run(instancesWithContext).flatMap(instances => {
      val instanceMap = mutable.Map[SlickFlowInstance, mutable.Map[String, String]]()

      instances.foreach {
        case (instance, Some(context)) => instanceMap.getOrElseUpdate(instance, mutable.Map.empty).update(context.key, context.value)
        case _ =>
      }

      Future.successful(instanceMap.map {
        case (instance, instanceContext) => InstanceWithContext(instance, instanceContext.toMap)
      }.toSeq)
    })
  }

  override def createFlowInstance(
    flowDefinitionId: String,
    context: Map[String, String])(implicit repositoryContext: RepositoryContext): Future[FlowInstance] = {
    val newInstance = SlickFlowInstance(
      id = UUID.randomUUID().toString,
      flowDefinitionId = flowDefinitionId,
      created = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
      version = 0L,
      creator = repositoryContext.currentUser,
      updated = None,
      status = "new",
      retries = 3,
      startTime = None,
      endTime = None)

    val contextActions = context.map {
      case (key, value) =>
        contextTable += SysiphosFlowInstanceContext(UUID.randomUUID().toString, newInstance.id, key, value)
    }.toSeq

    db.run(DBIO.seq(contextActions: _*) >> (instanceTable += newInstance).transactionally).map(_ => InstanceWithContext(newInstance, context))
  }

  override def counts(flowDefinitionId: Option[Seq[String]], status: Option[Seq[String]]): Future[Seq[InstanceCount]] = {
    val countQuery: DBIOAction[Seq[InstanceCount], NoStream, Read] = instanceTable
      .filter(instance => flowDefinitionId.map(instance.flowDefinitionId.inSet(_)).getOrElse(instance.id === instance.id))
      .filter(instance => status.map(instance.status.inSet(_)).getOrElse(instance.id === instance.id))
      .groupBy(q => (q.flowDefinitionId, q.status))
      .map {
        case ((idValue, statusValue), groupedByIdAndStatus) => (idValue, statusValue, groupedByIdAndStatus.length)
      }.result.map(_.map(InstanceCount.tupled))

    db.run(countQuery)
  }

}
