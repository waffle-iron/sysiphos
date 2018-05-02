package com.flowtick.sysiphos.api

import java.io.File
import java.util.concurrent.Executors

import akka.actor.{ActorSystem, Props}
import com.flowtick.sysiphos.api.resources.{GraphIQLResources, TwitterBootstrapResources, UIResources}
import com.flowtick.sysiphos.execution.AkkaFlowExecutor.Init
import com.flowtick.sysiphos.execution.{AkkaFlowExecutor, CronScheduler}
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.git.{GitFlowDefinitionRepository, GitFlowScheduleRepository}
import com.flowtick.sysiphos.scheduler._
import com.flowtick.sysiphos.slick.SlickFlowInstanceRepository
import com.twitter.finagle.{Http, ListeningServer}
import com.twitter.util.Await
import io.finch.Application
import io.finch.circe._
import monix.execution.Scheduler

import scala.concurrent.ExecutionContext

trait SysiphosApiServer extends SysiphosApi
  with SysiphosApiServerConfig
  with GraphIQLResources
  with TwitterBootstrapResources
  with UIResources {

  def apiContext: SysiphosApiContext

  implicit val executionContext: ExecutionContext
  implicit def executorSystem: ActorSystem
  implicit def scheduler: Scheduler

  def startExecutorSystem(): Unit = {
    val executorActorProps = Props(
      classOf[AkkaFlowExecutor],
      apiContext.flowScheduleRepository,
      apiContext.flowInstanceRepository,
      CronScheduler: FlowScheduler,
      scheduler
    )

    val executorActor = executorSystem.actorOf(executorActorProps)

    executorActor ! Init()
  }

  def startApiServer: ListeningServer = {
    val service = (api :+: graphiqlResources :+: bootstrapResources :+: uiResources).toServiceAs[Application.Json]
    Await.ready(Http.server.serve(s"$bindAddress:$httpPort", service))
  }

}

object SysiphosApiServerApp extends SysiphosApiServer with App {
  val flowDefinitionRepository: FlowDefinitionRepository = new GitFlowDefinitionRepository(new File(repoBaseDir, "flows"), flowDefinitionsRemoteUrl, None)
  val flowScheduleRepository: FlowScheduleRepository = new GitFlowScheduleRepository(new File(repoBaseDir, "schedules"), flowSchedulesRemoteUrl, None)
  val flowInstanceRepository: FlowInstanceRepository = new SlickFlowInstanceRepository(dataSource)(dbProfile, ExecutionContext.fromExecutor(Executors.newWorkStealingPool(instanceThreads)))

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val executorSystem: ActorSystem = ActorSystem()
  implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

  val apiContext = new SysiphosApiContext(flowDefinitionRepository, flowScheduleRepository, flowInstanceRepository)(ExecutionContext.fromExecutor(Executors.newWorkStealingPool(apiThreads)))
  startExecutorSystem()
  startApiServer
}
