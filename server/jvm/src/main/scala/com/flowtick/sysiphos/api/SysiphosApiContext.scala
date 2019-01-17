package com.flowtick.sysiphos.api

import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.logging.Logger
import com.flowtick.sysiphos.scheduler.{ FlowScheduleDetails, FlowScheduleRepository, FlowScheduleStateStore }

import scala.concurrent.{ ExecutionContext, Future }

class SysiphosApiContext(
  flowDefinitionRepository: FlowDefinitionRepository,
  flowScheduleRepository: FlowScheduleRepository,
  flowInstanceRepository: FlowInstanceRepository,
  flowScheduleStateStore: FlowScheduleStateStore,
  flowTaskInstanceRepository: FlowTaskInstanceRepository)(implicit executionContext: ExecutionContext, repositoryContext: RepositoryContext)
  extends ApiContext {
  override def schedules(id: Option[String], flowId: Option[String]): Future[Seq[FlowScheduleDetails]] =
    flowScheduleRepository.getFlowSchedules(None, flowId).map(_.filter(schedule => id.forall(_ == schedule.id)))

  override def definitions(id: Option[String]): Future[Seq[FlowDefinitionSummary]] =
    for {
      definitions <- flowDefinitionRepository.getFlowDefinitions.map(_.filter(definitionDetails => id.forall(_ == definitionDetails.id)))
      counts <- flowInstanceRepository.counts(Some(definitions.map(_.id)), None, None)
    } yield {
      val countsById = counts.groupBy(_.flowDefinitionId)
      definitions.map { definitionDetails =>
        FlowDefinitionSummary(definitionDetails.id, countsById.getOrElse(definitionDetails.id, Seq.empty))
      }
    }

  override def definition(id: String): Future[Option[FlowDefinitionDetails]] =
    flowDefinitionRepository.findById(id)

  override def createOrUpdateFlowDefinition(source: String): Future[FlowDefinitionDetails] =
    FlowDefinition.fromJson(source) match {
      case Right(definition) =>
        flowDefinitionRepository.createOrUpdateFlowDefinition(definition)
      case Left(error) => Future.failed(error)
    }

  override def createFlowSchedule(
    id: Option[String],
    flowDefinitionId: String,
    flowTaskId: Option[String],
    expression: Option[String],
    enabled: Option[Boolean],
    backFill: Option[Boolean]): Future[FlowScheduleDetails] = {
    flowScheduleRepository.createFlowSchedule(
      id,
      expression,
      flowDefinitionId,
      flowTaskId,
      enabled,
      backFill)
  }

  override def setDueDate(flowScheduleId: String, dueDate: Long): Future[Boolean] = {
    flowScheduleStateStore.setDueDate(flowScheduleId, dueDate).map(_ => true)
  }

  override def updateFlowSchedule(
    id: String,
    expression: Option[String],
    enabled: Option[Boolean],
    backFill: Option[Boolean]): Future[FlowScheduleDetails] = {
    flowScheduleRepository.updateFlowSchedule(id, expression, enabled, backFill)
  }

  override def instances(
    flowDefinitionId: Option[String],
    instanceIds: Option[Seq[String]],
    status: Option[Seq[String]],
    createdGreaterThan: Option[Long],
    createdSmallerThan: Option[Long]): Future[Seq[FlowInstanceDetails]] = {
    flowInstanceRepository.getFlowInstances(FlowInstanceQuery(flowDefinitionId, instanceIds, status.map(_.map(FlowInstanceStatus.withName)), createdGreaterThan, createdSmallerThan))
  }

  override def taskInstances(
    flowInstanceId: Option[String],
    dueBefore: Option[Long],
    status: Option[Seq[String]]): Future[Seq[FlowTaskInstanceDetails]] = {
    val query = FlowTaskInstanceQuery(
      flowInstanceId = flowInstanceId,
      dueBefore = dueBefore,
      status = status.map(_.map(FlowTaskInstanceStatus.withName)))

    flowTaskInstanceRepository.find(query)
  }

  override def createInstance(flowDefinitionId: String, context: Seq[FlowInstanceContextValue]): Future[FlowInstanceDetails] = {
    flowDefinitionRepository
      .findById(flowDefinitionId)
      .flatMap {
        case Some(_) => flowInstanceRepository.createFlowInstance(
          flowDefinitionId,
          context,
          FlowInstanceStatus.Triggered)
        case None => Future.failed(new IllegalArgumentException(s"flow definition with id $flowDefinitionId does not exist"))
      }
  }

  override def log(logId: String): Future[String] = Future(
    Logger.defaultLogger.getLog(logId).compile.toList.unsafeRunSync().mkString("\n"))

  override def deleteInstance(flowInstanceId: String): Future[String] = {
    flowInstanceRepository.deleteFlowInstance(flowInstanceId)
  }

  override def setTaskStatus(
    taskInstanceId: String,
    status: String,
    retries: Option[Int],
    nextRetry: Option[Long]): Future[Option[FlowTaskInstanceDetails]] = {
    flowTaskInstanceRepository.setStatus(taskInstanceId, FlowTaskInstanceStatus.withName(status), retries, nextRetry)
  }
}
