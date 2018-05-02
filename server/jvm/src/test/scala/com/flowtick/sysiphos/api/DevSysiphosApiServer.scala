package com.flowtick.sysiphos.api
import java.io.File
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import com.flowtick.sysiphos.flow.{FlowDefinitionRepository, FlowInstanceRepository}
import com.flowtick.sysiphos.git.{GitFlowDefinitionRepository, GitFlowScheduleRepository}
import com.flowtick.sysiphos.scheduler.FlowScheduleRepository
import com.flowtick.sysiphos.slick.SlickFlowInstanceRepository
import monix.execution.Scheduler

import scala.concurrent.ExecutionContext

object DevSysiphosApiServer extends SysiphosApiServer {
  val flowDefinitionRepository: FlowDefinitionRepository = new GitFlowDefinitionRepository(new File(repoBaseDir, "flows"), flowDefinitionsRemoteUrl, None)
  val flowScheduleRepository: FlowScheduleRepository = new GitFlowScheduleRepository(new File(repoBaseDir, "schedules"), flowSchedulesRemoteUrl, None)
  val flowInstanceRepository: FlowInstanceRepository = new SlickFlowInstanceRepository(dataSource)(dbProfile, ExecutionContext.fromExecutor(Executors.newWorkStealingPool(instanceThreads)))

  val apiContext = new SysiphosApiContext(flowDefinitionRepository, flowScheduleRepository, flowInstanceRepository)(ExecutionContext.fromExecutor(Executors.newWorkStealingPool(apiThreads)))

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val executorSystem: ActorSystem = ActorSystem()
  implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global
}
