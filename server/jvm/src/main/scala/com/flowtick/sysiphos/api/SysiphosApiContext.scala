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
  override def definitions(id: Option[String]): Future[Seq[FlowDefinitionDetails]] =
    flowDefinitionRepository.getFlowDefinitions.map(_.filter(definitionDetails => id.forall(_ == definitionDetails.definition.id)))
}
