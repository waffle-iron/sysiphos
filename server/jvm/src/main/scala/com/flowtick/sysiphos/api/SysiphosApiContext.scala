package com.flowtick.sysiphos.api

import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository, FlowScheduleStateStore }

import scala.concurrent.{ ExecutionContext, Future }

class SysiphosApiContext(
  val flowDefinitionRepository: FlowDefinitionRepository,
  val flowScheduleRepository: FlowScheduleRepository[FlowSchedule],
  val flowInstanceRepository: FlowInstanceRepository[FlowInstance],
  val flowScheduleStateStore: FlowScheduleStateStore)(implicit executionContext: ExecutionContext, repositoryContext: RepositoryContext)
  extends ApiContext {
  override def schedules(id: Option[String]): Future[Seq[FlowSchedule]] =
    flowScheduleRepository.getFlowSchedules.map(_.filter(schedule => id.forall(_ == schedule.id)))
  override def definitions(id: Option[String]): Future[Seq[FlowDefinitionSummary]] =
    for {
      definitions <- flowDefinitionRepository.getFlowDefinitions.map(_.filter(definitionDetails => id.forall(_ == definitionDetails.id)))
      counts <- flowInstanceRepository.counts(Some(definitions.map(_.id)), None)
    } yield {
      val countsById = counts.groupBy(_.flowDefinitionId)
      definitions.map { definitionDetails =>
        FlowDefinitionSummary(definitionDetails.id, countsById.getOrElse(definitionDetails.id, Seq.empty))
      }
    }

  override def definition(id: String): Future[Option[FlowDefinitionDetails]] =
    flowDefinitionRepository.findById(id)

  override def createOrUpdate(source: String): Future[FlowDefinitionDetails] =
    FlowDefinition.fromJson(source) match {
      case Right(definition) =>
        flowDefinitionRepository.createOrUpdateFlowDefinition(definition)
      case Left(error) => Future.failed(error)
    }
}
