package com.flowtick.sysiphos.api

import java.util.concurrent.Executors

import akka.actor.{ ActorSystem, Props }
import cats.effect._
import cats.syntax.all._
import com.flowtick.sysiphos.api.resources.{ GraphIQLResources, TwitterBootstrapResources, UIResources }
import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowExecutorActor.Init
import com.flowtick.sysiphos.execution.Monitoring.DataDogStatsDReporter
import com.flowtick.sysiphos.execution.{ CronScheduler, FlowExecutorActor }
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler._
import com.flowtick.sysiphos.slick._
import com.twitter.finagle.{ Http, ListeningServer }
import io.finch.Application
import io.finch.circe._
import javax.sql.DataSource
import kamon.Kamon
import kamon.prometheus.PrometheusReporter
import kamon.system.SystemMetrics
import monix.execution.Scheduler
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.ExecutionContext

trait SysiphosApiServer extends SysiphosApi
  with SysiphosApiServerConfig
  with GraphIQLResources
  with TwitterBootstrapResources
  with UIResources {

  val log: Logger = LoggerFactory.getLogger(getClass)

  val flowDefinitionRepository: FlowDefinitionRepository
  val flowScheduleRepository: SlickFlowScheduleRepository
  val flowInstanceRepository: FlowInstanceRepository
  val flowTaskInstanceRepository: FlowTaskInstanceRepository

  def apiContext(repositoryContext: RepositoryContext): SysiphosApiContext

  implicit val executionContext: ExecutionContext
  implicit def executorSystem: ActorSystem
  implicit def scheduler: Scheduler

  def startExecutorSystem(
    flowScheduleRepository: FlowScheduleRepository,
    flowInstanceRepository: FlowInstanceRepository,
    flowScheduleStateStore: FlowScheduleStateStore,
    flowDefinitionRepository: FlowDefinitionRepository,
    flowTaskInstanceRepository: FlowTaskInstanceRepository): Unit = {
    val executorActorProps = Props[FlowExecutorActor](new FlowExecutorActor(
      flowScheduleRepository,
      flowInstanceRepository,
      flowDefinitionRepository,
      flowTaskInstanceRepository,
      flowScheduleStateStore,
      CronScheduler)(scheduler))

    val executorActor = executorSystem.actorOf(executorActorProps)

    executorActor ! Init()
  }

  def bindServerToAddress: IO[ListeningServer] = IO {
    val logo = scala.io.Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("logo.txt")).getLines().mkString("\n")
    log.info(s"starting ...\n$logo")

    val address = s"$bindAddress:$httpPort"

    val service = (api :+: graphiqlResources :+: bootstrapResources :+: uiResources).toServiceAs[Application.Json]
    val server = Http.server.serve(address, service)

    log.info(s"running at ${server.boundAddress.toString}")

    server
  }

  def updateDatabase(): IO[Unit] =
    IO(log.info(s"using database profile $dbProfileName for migrations: $dbUrl"))
      .flatMap(_ => DefaultSlickRepositoryMigrations.updateDatabase(dataSource(dbProfile)))

  def addStatsReporter(): Unit = {
    if (Configuration.propOrEnv("stats.enabled", "false").toBoolean) {
      SystemMetrics.startCollecting()

      log.info("adding stats reporters...")

      Kamon.addReporter(new DataDogStatsDReporter)
      Kamon.addReporter(new PrometheusReporter)
    }
  }

  def startApiServer(): IO[Unit] = {
    addStatsReporter()

    val startedServer = for {
      _ <- updateDatabase()
      _ <- bindServerToAddress
    } yield startExecutorSystem(
      flowScheduleRepository,
      flowInstanceRepository,
      flowScheduleRepository,
      flowDefinitionRepository,
      flowTaskInstanceRepository)

    startedServer.handleErrorWith { error =>
      IO(log.error("unable to start server", error)) *>
        IO(executorSystem.terminate()) *>
        IO(SystemMetrics.stopCollecting()) *>
        IO.raiseError(new RuntimeException(s"unable to start server", error))
    }
  }

}

object SysiphosApiServerApp extends SysiphosApiServer with App {
  val slickExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(instanceThreads))
  val apiExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(apiThreads))

  lazy val repositoryDataSource: DataSource = dataSource(dbProfile)

  lazy val flowDefinitionRepository: FlowDefinitionRepository = new SlickFlowDefinitionRepository(repositoryDataSource)(dbProfile, slickExecutionContext)
  lazy val flowScheduleRepository: SlickFlowScheduleRepository = new SlickFlowScheduleRepository(repositoryDataSource)(dbProfile, slickExecutionContext)
  lazy val flowInstanceRepository: FlowInstanceRepository = new SlickFlowInstanceRepository(repositoryDataSource)(dbProfile, slickExecutionContext)
  lazy val flowTaskInstanceRepository: FlowTaskInstanceRepository = new SlickFlowTaskInstanceRepository(repositoryDataSource)(dbProfile, slickExecutionContext)

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit val executorSystem: ActorSystem = ActorSystem()
  implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

  def apiContext(repositoryContext: RepositoryContext) = new SysiphosApiContext(
    flowDefinitionRepository,
    flowScheduleRepository,
    flowInstanceRepository,
    flowScheduleRepository,
    flowTaskInstanceRepository)(apiExecutionContext, repositoryContext)

  startApiServer().unsafeRunSync()
}
