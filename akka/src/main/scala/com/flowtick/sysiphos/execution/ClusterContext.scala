package com.flowtick.sysiphos.execution

import cats.data.Reader
import com.flowtick.sysiphos.flow.{ FlowDefinitionRepository, FlowInstanceRepository, FlowTaskInstanceRepository }
import com.flowtick.sysiphos.scheduler.{ FlowScheduleRepository, FlowScheduleStateStore }

trait ClusterContext {
  def flowScheduleRepository: FlowScheduleRepository
  def flowInstanceRepository: FlowInstanceRepository
  def flowScheduleStateStore: FlowScheduleStateStore
  def flowDefinitionRepository: FlowDefinitionRepository
  def flowTaskInstanceRepository: FlowTaskInstanceRepository
}

object ClusterContext {
  type ClusterContextProvider = Reader[Unit, ClusterContext]
}

final case class DefaultClusterContext(
  flowScheduleRepository: FlowScheduleRepository,
  flowInstanceRepository: FlowInstanceRepository,
  flowScheduleStateStore: FlowScheduleStateStore,
  flowDefinitionRepository: FlowDefinitionRepository,
  flowTaskInstanceRepository: FlowTaskInstanceRepository) extends ClusterContext

object StaticClusterContext {
  private var flowDefinitionRepositoryOption: Option[FlowDefinitionRepository] = None
  private var flowInstanceRepositoryOption: Option[FlowInstanceRepository] = None
  private var flowScheduleRepositoryOption: Option[FlowScheduleRepository] = None
  private var flowScheduleStateStoreOption: Option[FlowScheduleStateStore] = None
  private var flowTaskInstanceRepositoryOption: Option[FlowTaskInstanceRepository] = None

  def init(
    flowScheduleRepository: FlowScheduleRepository,
    flowDefinitionRepository: FlowDefinitionRepository,
    flowInstanceRepository: FlowInstanceRepository,
    flowTaskInstanceRepository: FlowTaskInstanceRepository,
    flowScheduleStateStore: FlowScheduleStateStore): Unit = {
    this.flowDefinitionRepositoryOption = Some(flowDefinitionRepository)
    this.flowInstanceRepositoryOption = Some(flowInstanceRepository)
    this.flowTaskInstanceRepositoryOption = Some(flowTaskInstanceRepository)
    this.flowScheduleStateStoreOption = Some(flowScheduleStateStore)
    this.flowScheduleRepositoryOption = Some(flowScheduleRepository)
  }

  def instance: Option[ClusterContext] = for {
    flowScheduleStateStore <- flowScheduleStateStoreOption
    flowScheduleRepository <- flowScheduleRepositoryOption
    flowDefinitionRepository <- flowDefinitionRepositoryOption
    flowInstanceRepository <- flowInstanceRepositoryOption
    flowTaskInstanceRepository <- flowTaskInstanceRepositoryOption
  } yield DefaultClusterContext(flowScheduleRepository, flowInstanceRepository, flowScheduleStateStore, flowDefinitionRepository, flowTaskInstanceRepository)
}
