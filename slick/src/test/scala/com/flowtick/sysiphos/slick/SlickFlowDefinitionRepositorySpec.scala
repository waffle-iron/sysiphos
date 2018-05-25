package com.flowtick.sysiphos.slick

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.task.CommandLineTask
import slick.jdbc.H2Profile

class SlickFlowDefinitionRepositorySpec extends SlickSpec {
  val slickDefinitionRepository = new SlickFlowDefinitionRepository(dataSource)(H2Profile, scala.concurrent.ExecutionContext.Implicits.global)

  "Slick Definition Repository" should "create definition" in new RepositoryContext {
    override def currentUser: String = "test-user"

    val someDefinition = SysiphosDefinition(
      "foo",
      CommandLineTask("foo", None, "ls -la"))

    slickDefinitionRepository.getFlowDefinitions(this).futureValue should be(empty)
    slickDefinitionRepository.addFlowDefinition(someDefinition)(this).futureValue should be(someDefinition)
    slickDefinitionRepository.getFlowDefinitions(this).futureValue should have size 1
  }
}
