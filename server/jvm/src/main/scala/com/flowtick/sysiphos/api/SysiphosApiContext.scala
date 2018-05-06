package com.flowtick.sysiphos.api

import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowDefinitionRepository, FlowInstance, FlowInstanceRepository }
import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository, FlowScheduleStateStore }

import scala.concurrent.{ ExecutionContext, Future }

class SysiphosApiContext(
  val flowDefinitionRepository: FlowDefinitionRepository,
  val flowScheduleRepository: FlowScheduleRepository[FlowSchedule],
  val flowInstanceRepository: FlowInstanceRepository[FlowInstance],
  val flowScheduleStateStore: FlowScheduleStateStore)(implicit executionContext: ExecutionContext, repositoryContext: RepositoryContext) extends ApiContext {
  override def findSchedules(): Future[Seq[FlowSchedule]] = flowScheduleRepository.getFlowSchedules
  override def findFlowDefinitions(): Future[Seq[FlowDefinition]] = flowDefinitionRepository.getFlowDefinitions
  override def findSchedule(id: String): Future[Option[FlowSchedule]] =
    flowScheduleRepository.getFlowSchedules.map(_.find(_.id == id))
  override def findFlowDefinition(id: String): Future[Option[FlowDefinition]] =
    flowDefinitionRepository.getFlowDefinitions.map(_.find(_.id == id))
}
