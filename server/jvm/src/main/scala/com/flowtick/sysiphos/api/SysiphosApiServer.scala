package com.flowtick.sysiphos.api

import akka.actor.{ActorSystem, Props}
import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.api.resources.{GraphIQLResources, TwitterBootstrapResources, UIResources}
import com.flowtick.sysiphos.execution.{AkkaFlowExecutor, Init}
import com.flowtick.sysiphos.flow.FlowDefinition
import com.flowtick.sysiphos.scheduler.{CronSchedule, FlowSchedule, InMemoryFlowScheduleRepository}
import com.twitter.finagle.Http
import com.twitter.util.Await
import io.finch.Application
import io.finch.circe._
import monix.execution.Scheduler

import scala.concurrent.ExecutionContext

object SysiphosApiServer extends App
  with SysiphosApi
  with GraphIQLResources
  with TwitterBootstrapResources
  with UIResources {

  override val apiContext = new ApiContext {
    override def findSchedules(): Seq[FlowSchedule] = Seq.empty[FlowSchedule]
    override def findSchedule(id: String): Option[FlowSchedule] = Some(CronSchedule(id, "expr", "flowId"))
    override def findFlowDefinition(id: String): Option[FlowDefinition] = None
    override def findFlowDefinitions(): Seq[FlowDefinition] = Seq.empty
  }

  override implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  def startExecutorSystem() = {
    val flowScheduleRepository = new InMemoryFlowScheduleRepository(Seq.empty)
    implicit val exectuorSystem: ActorSystem = ActorSystem()
    implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

    val executorActorProps =
      Props[AkkaFlowExecutor](creator = new AkkaFlowExecutor(flowScheduleRepository))

    val executorActor = exectuorSystem.actorOf(executorActorProps)

    executorActor ! Init()
  }

  def startApiServer = {
    val service = (api :+: graphiqlResources :+: bootstrapResources :+: uiResources).toServiceAs[Application.Json]
    val port = sys.env.get("PORT0").orElse(sys.props.get("http.port")).getOrElse(8080).toString

    Await.ready(Http.server.serve("0.0.0.0:".concat(port), service))
  }

  startExecutorSystem()
  startApiServer
}
