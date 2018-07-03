package com.flowtick.sysiphos.slick

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.FlowDefinition
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.task.CommandLineTask
import slick.jdbc.H2Profile

import scala.util.Try

class SlickFlowDefinitionRepositorySpec extends SlickSpec {
  lazy val slickDefinitionRepository = new SlickFlowDefinitionRepository(dataSource)(H2Profile, scala.concurrent.ExecutionContext.Implicits.global)

  "Slick Definition Repository" should "create definition" in new RepositoryContext {
    override def currentUser: String = "test-user"

    val simpleDefinition = SysiphosDefinition(
      "foo",
      CommandLineTask("foo", None, "ls -la"))

    Try(slickDefinitionRepository.getFlowDefinitions(this).futureValue).failed.foreach(_.printStackTrace())
    slickDefinitionRepository.addFlowDefinition(simpleDefinition)(this).futureValue.source.map(FlowDefinition.fromJson) should be(Some(Right(simpleDefinition)))
    slickDefinitionRepository.getFlowDefinitions(this).futureValue should have size 1
  }
}
