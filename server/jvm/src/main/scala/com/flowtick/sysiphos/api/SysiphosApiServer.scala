package com.flowtick.sysiphos.api

import java.io.File

import akka.actor.{ ActorSystem, Props }
import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.api.resources.{ GraphIQLResources, TwitterBootstrapResources, UIResources }
import com.flowtick.sysiphos.execution.AkkaFlowExecutor.Init
import com.flowtick.sysiphos.execution.{ AkkaFlowExecutor, CronScheduler, FlowInstanceActor }
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.git.{ GitFlowDefinitionRepository, GitFlowScheduleRepository }
import com.flowtick.sysiphos.scheduler._
import com.twitter.finagle.Http
import com.twitter.util.Await
import io.finch.Application
import io.finch.circe._
import monix.execution.Scheduler

import scala.concurrent.{ ExecutionContext, Future }

trait SysiphosApiServer extends SysiphosApi
  with GraphIQLResources
  with TwitterBootstrapResources
  with UIResources {

  def apiContext: SysiphosApiContext

  implicit val executionContext: ExecutionContext
  implicit def executorSystem: ActorSystem
  implicit def scheduler: Scheduler

  def startExecutorSystem() = {
    val instanceActorProps = Props(classOf[FlowInstanceActor], apiContext.flowInstanceRepository)
    val executorActorProps = Props(classOf[AkkaFlowExecutor], apiContext.flowScheduleRepository, CronScheduler: FlowScheduler, instanceActorProps, scheduler)

    val executorActor = executorSystem.actorOf(executorActorProps)

    executorActor ! Init()
  }

  def startApiServer = {
    val service = (api :+: graphiqlResources :+: bootstrapResources :+: uiResources).toServiceAs[Application.Json]
    val port = sys.env.get("PORT0").orElse(sys.props.get("http.port")).getOrElse(8080).toString

    Await.ready(Http.server.serve("0.0.0.0:".concat(port), service))
  }

}

class SysiphosApiContext(
  val flowDefinitionRepository: FlowDefinitionRepository,
  val flowScheduleRepository: FlowScheduleRepository,
  val flowInstanceRepository: FlowInstanceRepository) extends ApiContext {
  override def findSchedules(): Future[Seq[FlowSchedule]] = flowScheduleRepository.getFlowSchedules
  override def findFlowDefinitions(): Future[Seq[FlowDefinition]] = flowDefinitionRepository.getFlowDefinitions
  override def findSchedule(id: String): Option[FlowSchedule] = ???
  override def findFlowDefinition(id: String): Option[FlowDefinition] = ???
}

object SysiphosApiServerApp extends SysiphosApiServer with App {
  val repoBaseDir = new File(".sysiphos")

  val flowDefinitionRepository: FlowDefinitionRepository = new GitFlowDefinitionRepository(new File(repoBaseDir, "flows"), None)
  val flowScheduleRepository: FlowScheduleRepository = new GitFlowScheduleRepository(new File(repoBaseDir, "schedules"), None)
  val flowInstanceRepository = new InMemoryFlowInstanceRepository

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val executorSystem: ActorSystem = ActorSystem()
  implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

  val apiContext = new SysiphosApiContext(flowDefinitionRepository, flowScheduleRepository, flowInstanceRepository)
  startExecutorSystem()
  startApiServer
}
