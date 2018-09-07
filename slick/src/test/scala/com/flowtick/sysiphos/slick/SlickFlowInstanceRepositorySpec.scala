package com.flowtick.sysiphos.slick

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowInstance, FlowInstanceContextValue, FlowInstanceQuery, FlowInstanceStatus }
import slick.jdbc.H2Profile

class SlickFlowInstanceRepositorySpec extends SlickSpec {
  val slickInstanceRepository = new SlickFlowInstanceRepository(dataSource)(H2Profile, scala.concurrent.ExecutionContext.Implicits.global)

  "Slick Instance Repository" should "create instance" in new RepositoryContext {
    override def currentUser: String = "test-user"

    slickInstanceRepository.getFlowInstances.futureValue should be(empty)
    val newInstance: FlowInstance = slickInstanceRepository.createFlowInstance("some-definition", Map("foo" -> "bar"))(this).futureValue
    val instancesWithContext: Seq[FlowInstance] = slickInstanceRepository.getFlowInstances(FlowInstanceQuery(flowDefinitionId = Some("some-definition")))(this).futureValue
    instancesWithContext.head.context should be(Seq(FlowInstanceContextValue("foo", "bar")))
    instancesWithContext.head.status should be(FlowInstanceStatus.New)
  }
}
