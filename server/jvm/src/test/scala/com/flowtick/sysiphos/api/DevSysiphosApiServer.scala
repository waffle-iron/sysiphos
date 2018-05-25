package com.flowtick.sysiphos.api
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.slick.{ DefaultSlickRepositoryMigrations, SlickFlowDefinitionRepository, SlickFlowInstanceRepository, SlickFlowScheduleRepository }
import monix.execution.Scheduler

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

object DevSysiphosApiServer extends App with SysiphosApiServer {
  val slickExecutor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(instanceThreads))
  val apiExecutor = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(apiThreads))

  val flowDefinitionRepository: SlickFlowDefinitionRepository = new SlickFlowDefinitionRepository(dataSource)(dbProfile, slickExecutor)
  val flowScheduleRepository: SlickFlowScheduleRepository = new SlickFlowScheduleRepository(dataSource)(dbProfile, slickExecutor)
  val flowInstanceRepository: SlickFlowInstanceRepository = new SlickFlowInstanceRepository(dataSource)(dbProfile, slickExecutor)

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val executorSystem: ActorSystem = ActorSystem()
  implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

  def apiContext(repositoryContext: RepositoryContext) = new SysiphosApiContext(flowDefinitionRepository, flowScheduleRepository, flowInstanceRepository, flowScheduleRepository)(apiExecutor, repositoryContext)

  DefaultSlickRepositoryMigrations.updateDatabase

  startExecutorSystem(flowScheduleRepository, flowInstanceRepository, flowScheduleRepository)
  startApiServer
}
