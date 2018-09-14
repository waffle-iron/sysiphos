package com.flowtick.sysiphos.slick

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow._
import slick.jdbc.H2Profile

class SlickFlowTaskInstanceRepositorySpec extends SlickSpec {
  val slickFlowTaskInstanceRepository = new SlickFlowTaskInstanceRepository(dataSource)(H2Profile, scala.concurrent.ExecutionContext.Implicits.global)

  "Slick Flow Task Instance Repository" should "create instance" in new RepositoryContext {
    override def currentUser: String = "test-user"

    slickFlowTaskInstanceRepository.getFlowTaskInstances.futureValue should be(empty)
    val newInstance: FlowTaskInstance = slickFlowTaskInstanceRepository.createFlowTaskInstance("some-definition", "some-task-id")(this).futureValue
    val instancesWithContext: Seq[FlowTaskInstance] = slickFlowTaskInstanceRepository.getFlowTaskInstances("some-definition")(this).futureValue
    instancesWithContext.map(_.id) should contain(newInstance.id)
  }

  it should "update status" in new RepositoryContext {
    override def currentUser: String = "test-user"

    val newInstance: FlowTaskInstance = slickFlowTaskInstanceRepository.createFlowTaskInstance("some-definition_retries", "some-task-id")(this).futureValue

    slickFlowTaskInstanceRepository.setStatus(newInstance.id, FlowTaskInstanceStatus.Failed)(this).futureValue

    val updatedInstance: Option[FlowTaskInstance] = slickFlowTaskInstanceRepository.setRetries(newInstance.id, 42)(this).futureValue
    updatedInstance.head.status should be(FlowTaskInstanceStatus.Failed)
    updatedInstance.head.retries should be(42)
  }

  it should "set log id" in new RepositoryContext {
    override def currentUser: String = "test-user"

    val newInstance: FlowTaskInstanceDetails = slickFlowTaskInstanceRepository.createFlowTaskInstance("some-definition_retries", "some-task-id")(this).futureValue
    val logId = "somelogid"

    slickFlowTaskInstanceRepository
      .setLogId(newInstance.id, logId)(this)
      .futureValue should be(Some(newInstance.copy(logId = Some(logId))))
  }
}
