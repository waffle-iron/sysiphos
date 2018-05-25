package com.flowtick.sysiphos.slick

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowInstance, FlowInstanceQuery }
import slick.jdbc.H2Profile

class SlickFlowInstanceRepositorySpec extends SlickSpec {
  val slickInstanceRepository = new SlickFlowInstanceRepository(dataSource)(H2Profile, scala.concurrent.ExecutionContext.Implicits.global)

  "Slick Instance Repository" should "create instance" in new RepositoryContext {
    override def currentUser: String = "test-user"

    slickInstanceRepository.getFlowInstances.futureValue should be(empty)
    val newInstance: FlowInstance = slickInstanceRepository.createFlowInstance("some-definition", Map("foo" -> "bar"))(this).futureValue
    val instancesWithContext: Seq[FlowInstance] = slickInstanceRepository.getFlowInstances(FlowInstanceQuery(flowDefinitionId = "some-definition"))(this).futureValue
    instancesWithContext.head.context should be(Map("foo" -> "bar"))
    instancesWithContext.head.status should be("new")
  }
}
