package com.flowtick.sysiphos.api

import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.flow.{FlowDefinition, FlowDefinitionRepository, FlowInstanceRepository}
import com.flowtick.sysiphos.scheduler.{FlowSchedule, FlowScheduleRepository}

import scala.concurrent.{ExecutionContext, Future}

class SysiphosApiContext(
  val flowDefinitionRepository: FlowDefinitionRepository,
  val flowScheduleRepository: FlowScheduleRepository,
  val flowInstanceRepository: FlowInstanceRepository)(implicit executionContext: ExecutionContext) extends ApiContext {
  override def findSchedules(): Future[Seq[FlowSchedule]] = flowScheduleRepository.getFlowSchedules
  override def findFlowDefinitions(): Future[Seq[FlowDefinition]] = flowDefinitionRepository.getFlowDefinitions
  override def findSchedule(id: String): Future[Option[FlowSchedule]] =
    flowScheduleRepository.getFlowSchedules.map(_.find(_.id == id))
  override def findFlowDefinition(id: String): Future[Option[FlowDefinition]] =
    flowDefinitionRepository.getFlowDefinitions.map(_.find(_.id == id))
}
