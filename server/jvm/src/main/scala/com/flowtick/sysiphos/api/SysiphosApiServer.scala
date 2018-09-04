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
import com.twitter.finagle.Http
import io.finch.Application
import io.finch.circe._
import monix.execution.Scheduler
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

trait SysiphosApiServer extends SysiphosApi
  with SysiphosApiServerConfig
  with GraphIQLResources
  with TwitterBootstrapResources
  with UIResources {

  val log: Logger = LoggerFactory.getLogger(getClass)

  val flowDefinitionRepository: FlowDefinitionRepository
  val flowScheduleRepository: SlickFlowScheduleRepository
  val flowInstanceRepository: FlowInstanceRepository
  val flowTaskInstanceRepository: FlowTaskInstanceRepository[FlowTaskInstance]

  def apiContext(repositoryContext: RepositoryContext): SysiphosApiContext

  implicit val executionContext: ExecutionContext
  implicit def executorSystem: ActorSystem
  implicit def scheduler: Scheduler

  def startExecutorSystem(
    flowScheduleRepository: FlowScheduleRepository,
    flowInstanceRepository: FlowInstanceRepository,
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

  def startApiServer(): Unit = {
    val address = s"$bindAddress:$httpPort"

    Try {
      DefaultSlickRepositoryMigrations.updateDatabase(dataSource)

      val service = (api :+: graphiqlResources :+: bootstrapResources :+: uiResources).toServiceAs[Application.Json]
      val server = Http.server.serve(address, service)

      log.info(s"running at ${server.boundAddress.toString}")

      server
    }.recoverWith {
      case error => Failure(new RuntimeException(s"unable to start server at $address", error))
    } match {
      case Success(_) =>
        startExecutorSystem(flowScheduleRepository, flowInstanceRepository, flowScheduleRepository, flowDefinitionRepository, flowTaskInstanceRepository)
      case Failure(error) =>
        log.error("unable to start server", error)
        executorSystem.terminate()
    }

  }

}

object SysiphosApiServerApp extends SysiphosApiServer with App {
  val slickExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(instanceThreads))
  val apiExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(apiThreads))

  lazy val flowDefinitionRepository: FlowDefinitionRepository = new SlickFlowDefinitionRepository(dataSource)(dbProfile, slickExecutionContext)
  lazy val flowScheduleRepository: SlickFlowScheduleRepository = new SlickFlowScheduleRepository(dataSource)(dbProfile, slickExecutionContext)
  lazy val flowInstanceRepository: FlowInstanceRepository = new SlickFlowInstanceRepository(dataSource)(dbProfile, slickExecutionContext)
  lazy val flowTaskInstanceRepository: FlowTaskInstanceRepository[FlowTaskInstance] = new SlickFlowTaskInstanceRepository(dataSource)(dbProfile, slickExecutionContext)

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit val executorSystem: ActorSystem = ActorSystem()
  implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

  def apiContext(repositoryContext: RepositoryContext) = new SysiphosApiContext(flowDefinitionRepository, flowScheduleRepository, flowInstanceRepository, flowScheduleRepository)(apiExecutionContext, repositoryContext)

  startApiServer()
}
