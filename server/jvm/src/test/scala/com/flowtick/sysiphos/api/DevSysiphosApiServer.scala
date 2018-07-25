package com.flowtick.sysiphos.api

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.slick.{ DefaultSlickRepositoryMigrations, SlickFlowDefinitionRepository, SlickFlowInstanceRepository, SlickFlowScheduleRepository }
import com.flowtick.sysiphos.task.CommandLineTask
import monix.execution.Scheduler
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }
import scala.util.Try

object DevSysiphosApiServer extends App with SysiphosApiServer with ScalaFutures with IntegrationPatience {
  val slickExecutor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(instanceThreads))
  val apiExecutor = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(apiThreads))

  val flowDefinitionRepository: SlickFlowDefinitionRepository = new SlickFlowDefinitionRepository(dataSource)(dbProfile, slickExecutor)
  val flowScheduleRepository: SlickFlowScheduleRepository = new SlickFlowScheduleRepository(dataSource)(dbProfile, slickExecutor)
  val flowInstanceRepository: SlickFlowInstanceRepository = new SlickFlowInstanceRepository(dataSource)(dbProfile, slickExecutor)

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val executorSystem: ActorSystem = ActorSystem()
  implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

  def apiContext(repositoryContext: RepositoryContext) = new SysiphosApiContext(flowDefinitionRepository, flowScheduleRepository, flowInstanceRepository, flowScheduleRepository)(apiExecutor, repositoryContext)

  DefaultSlickRepositoryMigrations.updateDatabase(dataSource)

  implicit val repositoryContext = new RepositoryContext {
    override def currentUser: String = "dev-test"
  }

  Try {
    val definitionDetails = flowDefinitionRepository.createOrUpdateFlowDefinition(SysiphosDefinition(
      "foo",
      CommandLineTask("foo", None, "ls -la"))).futureValue

    val definitionDetails2 = flowDefinitionRepository.createOrUpdateFlowDefinition(SysiphosDefinition(
      "foo2",
      CommandLineTask("foo", None, "ls -la"))).futureValue

    flowScheduleRepository.createFlowSchedule(
      Some("test-schedule"),
      Some("0,15,30,45 * * ? * *"),
      definitionDetails.id,
      None,
      Some(true)).futureValue
  }

  startExecutorSystem(flowScheduleRepository, flowInstanceRepository, flowScheduleRepository, flowDefinitionRepository)
  startApiServer
}
