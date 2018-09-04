package com.flowtick.sysiphos.api

import java.util.concurrent.Executors

import akka.actor.{ ActorSystem, Props }
import com.flowtick.sysiphos.api.resources.{ GraphIQLResources, TwitterBootstrapResources, UIResources }
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowExecutorActor.Init
import com.flowtick.sysiphos.execution.{ CronScheduler, FlowExecutorActor }
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler._
import com.flowtick.sysiphos.slick._
import com.twitter.finagle.{ Http, ListeningServer }
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

  def apiContext(repositoryContext: RepositoryContext): SysiphosApiContext

  implicit val executionContext: ExecutionContext
  implicit def executorSystem: ActorSystem
  implicit def scheduler: Scheduler

  def startExecutorSystem(
    flowScheduleRepository: FlowScheduleRepository,
    flowInstanceRepository: FlowInstanceRepository[FlowInstance],
    flowScheduleStateStore: FlowScheduleStateStore,
    flowDefinitionRepository: FlowDefinitionRepository,
    flowTaskInstanceRepository: FlowTaskInstanceRepository[FlowTaskInstance]): Unit = {
    val executorActorProps = Props(
      classOf[FlowExecutorActor],
      flowScheduleRepository,
      flowInstanceRepository,
      flowDefinitionRepository,
      flowTaskInstanceRepository,
      flowScheduleStateStore,
      CronScheduler: FlowScheduler)

    val executorActor = executorSystem.actorOf(executorActorProps)

    executorActor ! Init()
  }

  def startApiServer: ListeningServer = {
    val service = (api :+: graphiqlResources :+: bootstrapResources :+: uiResources).toServiceAs[Application.Json]
    Await.ready(Http.server.serve(s"$bindAddress:$httpPort", service))
  }

}

object SysiphosApiServerApp extends SysiphosApiServer with App {
  val slickExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(instanceThreads))
  val apiExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(apiThreads))

  lazy val flowDefinitionRepository: FlowDefinitionRepository = new SlickFlowDefinitionRepository(dataSource)(dbProfile, slickExecutionContext)
  lazy val flowScheduleRepository: SlickFlowScheduleRepository = new SlickFlowScheduleRepository(dataSource)(dbProfile, slickExecutionContext)
  lazy val flowInstanceRepository: FlowInstanceRepository[FlowInstance] = new SlickFlowInstanceRepository(dataSource)(dbProfile, slickExecutionContext)
  lazy val flowTaskInstanceRepository: FlowTaskInstanceRepository[FlowTaskInstance] = new SlickFlowTaskInstanceRepository(dataSource)(dbProfile, slickExecutionContext)

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit val executorSystem: ActorSystem = ActorSystem()
  implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

  def apiContext(repositoryContext: RepositoryContext) = new SysiphosApiContext(flowDefinitionRepository, flowScheduleRepository, flowInstanceRepository, flowScheduleRepository)(apiExecutionContext, repositoryContext)

  DefaultSlickRepositoryMigrations.updateDatabase(dataSource)

  startExecutorSystem(flowScheduleRepository, flowInstanceRepository, flowScheduleRepository, flowDefinitionRepository, flowTaskInstanceRepository)

  startApiServer
}
