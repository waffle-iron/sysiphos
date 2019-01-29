package com.flowtick.sysiphos.api

import com.flowtick.sysiphos.core.DefaultRepositoryContext
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.task.CommandLineTask
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }

import scala.concurrent.ExecutionContext
import scala.util.Try

object DevSysiphosApiServer extends App with SysiphosApiServer with ScalaFutures with IntegrationPatience {
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val repositoryContext = new DefaultRepositoryContext("dev-test")

  startApiServer(clusterContext).unsafeRunSync()

  Try {
    val definitionDetails = flowDefinitionRepository.createOrUpdateFlowDefinition(SysiphosDefinition(
      "foo",
      Seq.tabulate(10)(index => CommandLineTask(s"foo-$index", None, "ls -la", shell = Some("bash"))))).futureValue

    flowScheduleRepository.createFlowSchedule(
      Some("test-schedule-2"),
      Some("0 * * ? * *"),
      definitionDetails.id,
      None,
      Some(true),
      None).futureValue
  }.failed.foreach(log.warn("unable to create dev schedule", _))
}
