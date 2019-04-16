package com.flowtick.sysiphos.api

import java.util.concurrent.{ Executors, TimeUnit }

import akka.actor.{ ActorSystem, Props }
import cats.data.Reader
import cats.effect._
import cats.syntax.all._
import com.flowtick.sysiphos.api.SysiphosApi.ApiContext
import com.flowtick.sysiphos.api.resources.{ GraphIQLResources, HealthCheck, TwitterBootstrapResources, UIResources }
import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.core.DefaultRepositoryContext
import com.flowtick.sysiphos.execution.ClusterContext.ClusterContextProvider
import com.flowtick.sysiphos.execution.FlowExecutorActor.Init
import com.flowtick.sysiphos.execution._
import com.flowtick.sysiphos.execution.cluster.{ ClusterActors, ClusterSetup }
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.logging
import com.flowtick.sysiphos.slick._
import com.twitter.finagle.{ Http, ListeningServer }
import io.finch.Application
import io.finch.circe._
import javax.sql.DataSource
import kamon.Kamon
import kamon.prometheus.PrometheusReporter
import kamon.statsd.StatsDReporter
import kamon.system.SystemMetrics
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

trait SysiphosApiServer extends SysiphosApi
  with SysiphosApiServerConfig
  with ClusterSetup
  with GraphIQLResources
  with TwitterBootstrapResources
  with UIResources
  with HealthCheck {

  val log: Logger = LoggerFactory.getLogger(getClass)

  val slickExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(instanceThreads))

  lazy val repositoryDataSource: DataSource = dataSource(dbProfile)

  lazy val flowDefinitionRepository: FlowDefinitionRepository = new SlickFlowDefinitionRepository(repositoryDataSource)(dbProfile, slickExecutionContext)
  lazy val flowScheduleRepository: SlickFlowScheduleRepository = new SlickFlowScheduleRepository(repositoryDataSource)(dbProfile, slickExecutionContext)
  lazy val flowInstanceRepository: FlowInstanceRepository = new SlickFlowInstanceRepository(repositoryDataSource)(dbProfile, slickExecutionContext)
  lazy val flowTaskInstanceRepository: FlowTaskInstanceRepository = new SlickFlowTaskInstanceRepository(repositoryDataSource)(dbProfile, slickExecutionContext)

  StaticClusterContext.init(flowScheduleRepository, flowDefinitionRepository, flowInstanceRepository, flowTaskInstanceRepository, flowScheduleRepository)

  implicit lazy val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(apiThreads))
  implicit lazy val executorSystem: ActorSystem = ActorSystem(clusterName)

  def clusterContext: ClusterContextProvider = Reader(_ => StaticClusterContext.instance.get)

  def startExecutorSystem(clusterContextProvider: ClusterContextProvider): IO[ClusterActors] = {
    val clusterContext = clusterContextProvider.apply()

    val executorActorProps = Props[FlowExecutorActor](new FlowExecutorActor(
      clusterContext.flowScheduleRepository,
      clusterContext.flowInstanceRepository,
      clusterContext.flowDefinitionRepository,
      clusterContext.flowTaskInstanceRepository,
      clusterContext.flowScheduleStateStore,
      CronScheduler))

    setupCluster(executorSystem, executorActorProps, clusterName, clusterContextProvider)
  }

  def bindServerToAddress(context: ApiContext): IO[ListeningServer] = IO {
    val logo = scala.io.Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("logo.txt")).getLines().mkString("\n")
    log.info(s"starting ...\n$logo")

    val address = s"$bindAddress:$httpPort"

    val service = api(context) :+:
      healthEndpoint(repositoryDataSource, logging.Logger.defaultLogger) :+:
      graphiqlResources :+:
      bootstrapResources :+:
      uiResources

    val server = Http.server.serve(address, service.toServiceAs[Application.Json])

    log.info(s"running at ${server.boundAddress.toString}")

    server
  }

  def updateDatabase(): IO[Unit] =
    IO(log.info(s"using database profile $dbProfileName for migrations: $dbUrl"))
      .flatMap(_ => DefaultSlickRepositoryMigrations.updateDatabase(dataSource(dbProfile)))

  def addStatsReporter(): Unit = {
    SystemMetrics.startCollecting()

    if (Configuration.propOrEnv("stats.enabled", "false").toBoolean) {
      log.info("adding prometheus reporter...")

      Kamon.addReporter(new PrometheusReporter)
    }

    if (Configuration.propOrEnv("statsd.enabled", "false").toBoolean) {
      log.info("adding statsd reporter...")

      Kamon.addReporter(new StatsDReporter)
    }
  }

  def startApiServer(clusterContext: ClusterContextProvider): IO[ClusterActors] = {
    addStatsReporter()

    val logger = logging.Logger.defaultLogger

    val startedServer: IO[ClusterActors] = for {
      _ <- updateDatabase().timeout(Duration(120, TimeUnit.SECONDS))
      _ <- IO(log.info("database migration finished."))
      _ <- logger.deleteLog(healthCheckLogId).timeout(Duration(5, TimeUnit.SECONDS))
      _ <- logger.appendLine(healthCheckLogId, "starting sysiphos server...").timeout(Duration(5, TimeUnit.SECONDS))
      _ <- IO(log.info("starting executor system..."))
      clusterActors <- startExecutorSystem(clusterContext)
      _ <- bindServerToAddress(new SysiphosApiContext(clusterContext(), clusterActors)(executionContext, new DefaultRepositoryContext("api")))
      _ <- IO(clusterActors.executorSingleton ! Init(clusterActors.workerPool))
    } yield clusterActors

    startedServer.handleErrorWith { error =>
      IO(log.error("unable to start server", error)) *>
        IO(executorSystem.terminate()) *>
        IO(SystemMetrics.stopCollecting()) *>
        IO.raiseError(new RuntimeException(s"unable to start server", error))
    }.timeout(Duration(300, TimeUnit.SECONDS))
  }

}

object SysiphosApiServerApp extends SysiphosApiServer with App {
  startApiServer(clusterContext).unsafeRunSync()
}
