package com.flowtick.sysiphos.slick

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow._
import slick.jdbc.H2Profile

class SlickFlowInstanceRepositorySpec extends SlickSpec {
  def createTestRepository = new SlickFlowInstanceRepository(dataSource, testIds)(H2Profile, scala.concurrent.ExecutionContext.Implicits.global)

  "Slick Instance Repository" should "create instance" in new RepositoryContext {
    override def currentUser: String = "test-user"
    override def epochSeconds: Long = 0

    val instanceRepository = createTestRepository

    instanceRepository.getFlowInstances.futureValue should be(empty)

    val newInstance: FlowInstance = instanceRepository.createFlowInstance("some-definition", Map("foo" -> "bar"), FlowInstanceStatus.Scheduled)(this).futureValue
    val instancesWithContext: Seq[FlowInstance] = instanceRepository.getFlowInstances(FlowInstanceQuery(flowDefinitionId = Some("some-definition"), None, None, None))(this).futureValue
    instancesWithContext.head.context should be(Seq(FlowInstanceContextValue("foo", "bar")))
    instancesWithContext.head.status should be(FlowInstanceStatus.Scheduled)
  }

  it should "find instances by query" in new RepositoryContext {
    override def currentUser: String = "test-user"
    override def epochSeconds: Long = 0

    val instanceRepository = createTestRepository

    val newInstance: FlowInstance =
      instanceRepository.createFlowInstance("some-definition", Map("foo" -> "bar"), FlowInstanceStatus.Scheduled)(this).futureValue

    val anotherInstance: FlowInstance =
      instanceRepository.createFlowInstance("some-definition", Map("bar" -> "baz", "mup" -> "moep"), FlowInstanceStatus.ManuallyTriggered)(this).futureValue

    instanceRepository
      .getFlowInstances(FlowInstanceQuery(flowDefinitionId = Some("some-definition")))(this)
      .futureValue should contain only (newInstance, anotherInstance)

    instanceRepository
      .getFlowInstances(FlowInstanceQuery(None, status = Some(FlowInstanceStatus.ManuallyTriggered)))(this)
      .futureValue should contain only anotherInstance
  }
}
