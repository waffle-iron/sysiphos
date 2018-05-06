package com.flowtick.sysiphos.api
import java.io.File
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.FlowDefinitionRepository
import com.flowtick.sysiphos.git.GitFlowDefinitionRepository
import com.flowtick.sysiphos.slick.{ SlickFlowInstanceRepository, SlickFlowScheduleRepository }
import monix.execution.Scheduler

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

object DevSysiphosApiServer extends SysiphosApiServer {
  val slickExecutor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(instanceThreads))
  val apiExecutor = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(apiThreads))

  val flowDefinitionRepository: FlowDefinitionRepository = new GitFlowDefinitionRepository(new File(repoBaseDir, "flows"), flowDefinitionsRemoteUrl, None)
  val flowScheduleRepository: SlickFlowScheduleRepository = new SlickFlowScheduleRepository(dataSource)(dbProfile, slickExecutor)
  val flowInstanceRepository: SlickFlowInstanceRepository = new SlickFlowInstanceRepository(dataSource)(dbProfile, slickExecutor)

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val executorSystem: ActorSystem = ActorSystem()
  implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

  def apiContext(repositoryContext: RepositoryContext) = new SysiphosApiContext(flowDefinitionRepository, flowScheduleRepository, flowInstanceRepository, flowScheduleRepository)(apiExecutor, repositoryContext)

}
