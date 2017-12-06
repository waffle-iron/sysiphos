package com.flowtick.sysiphos.api

import akka.actor.{ ActorSystem, Props }
import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.api.resources.{ GraphIQLResources, TwitterBootstrapResources, UIResources }
import com.flowtick.sysiphos.execution.AkkaFlowExecutor.Init
import com.flowtick.sysiphos.execution.{ AkkaFlowExecutor, CronScheduler, FlowInstanceActor }
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowInstance, FlowInstanceQuery, FlowInstanceRepository }
import com.flowtick.sysiphos.git.GitFlowScheduleRepository
import com.flowtick.sysiphos.scheduler._
import com.twitter.finagle.Http
import com.twitter.util.Await
import io.finch.Application
import io.finch.circe._
import monix.execution.Scheduler

import scala.concurrent.{ ExecutionContext, Future }

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
    val flowScheduleRepository: FlowScheduleRepository = new GitFlowScheduleRepository()
    val flowInstanceRepository = new FlowInstanceRepository {
      override def getFlowInstances(query: FlowInstanceQuery): Future[Seq[FlowInstance]] = ???
    }
    implicit val executorSystem: ActorSystem = ActorSystem()
    implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

    val instanceActorProps = Props(classOf[FlowInstanceActor], flowInstanceRepository)
    val executorActorProps = Props(classOf[AkkaFlowExecutor], flowScheduleRepository, CronScheduler: FlowScheduler, instanceActorProps, scheduler)

    val executorActor = executorSystem.actorOf(executorActorProps)

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
