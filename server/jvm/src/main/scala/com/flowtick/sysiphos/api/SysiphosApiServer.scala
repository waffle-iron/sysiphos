package com.flowtick.sysiphos.api

import akka.actor.{ActorSystem, Props}
import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.execution.{AkkaFlowExecutor, Init}
import com.flowtick.sysiphos.flow.FlowDefinition
import com.flowtick.sysiphos.scheduler.{CronSchedule, FlowSchedule, InMemoryFlowScheduleRepository}
import com.twitter.finagle.Http
import com.twitter.util.Await
import monix.execution.Scheduler

import scala.concurrent.ExecutionContext

object SysiphosApiServer extends SysiphosApi with App {

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

  def startApiServer = Await.ready(Http.server.serve(":" concat sys.props.getOrElse("http.port", 8080).toString, api))

  startExecutorSystem()
  startApiServer
}
