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

  implicit lazy val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit lazy val executorSystem: ActorSystem = ActorSystem(clusterName)

  implicit lazy val cs = cats.effect.IO.contextShift(executionContext)
  implicit lazy val timer = cats.effect.IO.timer(executionContext)

  lazy val repositoryDataSource: DataSource = dataSource(dbProfile)

  lazy val flowDefinitionRepository: FlowDefinitionRepository = new SlickFlowDefinitionRepository(repositoryDataSource)(dbProfile, executionContext)
  lazy val flowScheduleRepository: SlickFlowScheduleRepository = new SlickFlowScheduleRepository(repositoryDataSource)(dbProfile, executionContext)
  lazy val flowInstanceRepository: FlowInstanceRepository = new SlickFlowInstanceRepository(repositoryDataSource)(dbProfile, executionContext)
  lazy val flowTaskInstanceRepository: FlowTaskInstanceRepository = new SlickFlowTaskInstanceRepository(repositoryDataSource)(dbProfile, executionContext)

  StaticClusterContext.init(flowScheduleRepository, flowDefinitionRepository, flowInstanceRepository, flowTaskInstanceRepository, flowScheduleRepository)

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
    val logger = logging.Logger.defaultLogger

    val startedServer: IO[ClusterActors] = for {
      _ <- IO(addStatsReporter())
      _ <- IO(log.info("updating database...")) *> updateDatabase().timeout(Duration(120, TimeUnit.SECONDS)).retryWithBackoff(5)()

      _ <- IO(log.info("setting up health log...")) *>
        logger.deleteLog(healthCheckLogId).timeout(Duration(5, TimeUnit.SECONDS)) *>
        logger.appendLine(healthCheckLogId, "starting sysiphos server...").timeout(Duration(5, TimeUnit.SECONDS))

      clusterActors <- IO(log.info("starting executor system...")) *> startExecutorSystem(clusterContext)

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
