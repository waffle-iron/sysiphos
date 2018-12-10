package com.flowtick.sysiphos.slick

import com.flowtick.sysiphos.core.DefaultRepositoryContext
import com.flowtick.sysiphos.flow._
import slick.jdbc.H2Profile

class SlickFlowTaskInstanceRepositorySpec extends SlickSpec {
  val slickFlowTaskInstanceRepository = new SlickFlowTaskInstanceRepository(dataSource)(H2Profile, scala.concurrent.ExecutionContext.Implicits.global)

  "Slick Flow Task Instance Repository" should "create instance" in new DefaultRepositoryContext("test-user") {
    slickFlowTaskInstanceRepository.getFlowTaskInstances.futureValue should be(empty)
    val newInstance: FlowTaskInstance = slickFlowTaskInstanceRepository.createFlowTaskInstance("some-definition", "some-task-id", "log-id", 3, 10, None, None)(this).futureValue
    val instancesWithContext: Seq[FlowTaskInstance] = slickFlowTaskInstanceRepository.find(FlowTaskInstanceQuery(id = Some(newInstance.id)))(this).futureValue
    instancesWithContext.map(_.id) should contain(newInstance.id)
    instancesWithContext.head.logId should be("log-id")
  }

  it should "update status" in new DefaultRepositoryContext("test-user") {
    val newInstance: FlowTaskInstance = slickFlowTaskInstanceRepository.createFlowTaskInstance("some-definition_retries", "some-task-id", "log-id", 3, 10, None, None)(this).futureValue

    slickFlowTaskInstanceRepository.setStatus(newInstance.id, FlowTaskInstanceStatus.Failed)(this).futureValue

    val updatedInstance: Option[FlowTaskInstance] = slickFlowTaskInstanceRepository.setRetries(newInstance.id, 42)(this).futureValue
    updatedInstance.head.status should be(FlowTaskInstanceStatus.Failed)
    updatedInstance.head.retries should be(42)
  }

  it should "update time" in new DefaultRepositoryContext("test-user") {
    val newInstance: FlowTaskInstance = slickFlowTaskInstanceRepository.createFlowTaskInstance("some-definition_retries", "some-task-id", "log-id", 3, 10, None, None)(this).futureValue

    slickFlowTaskInstanceRepository.setStartTime(newInstance.id, 42)(this).futureValue
    val updatedInstance: Option[FlowTaskInstance] = slickFlowTaskInstanceRepository.setEndTime(newInstance.id, 43)(this).futureValue

    updatedInstance.head.startTime should be(Some(42))
    updatedInstance.head.endTime should be(Some(43))
  }

  it should "delete flow task instance" in new DefaultRepositoryContext("test-user") {
    val newInstance: FlowTaskInstance = slickFlowTaskInstanceRepository.createFlowTaskInstance("some-definition_retries", "some-task-id", "log-id", 3, 10, None, None)(this).futureValue

    slickFlowTaskInstanceRepository.findOne(FlowTaskInstanceQuery(Some(newInstance.id)))(this).futureValue should be(Some(newInstance))

    val deletedId = slickFlowTaskInstanceRepository.deleteFlowTaskInstance(newInstance.id)(this).futureValue

    deletedId should be(newInstance.id)

    slickFlowTaskInstanceRepository.findOne(FlowTaskInstanceQuery(Some(newInstance.id)))(this).futureValue should be(None)
  }

}
