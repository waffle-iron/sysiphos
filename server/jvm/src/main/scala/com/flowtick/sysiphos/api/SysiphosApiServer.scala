package com.flowtick.sysiphos.api

import java.util.concurrent.Executors

import akka.actor.{ ActorSystem, Props }
import cats.effect._
import cats.syntax.all._
import com.flowtick.sysiphos.api.resources.{ GraphIQLResources, TwitterBootstrapResources, UIResources }
import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowExecutorActor.Init
import com.flowtick.sysiphos.execution._
import com.flowtick.sysiphos.execution.cluster.ClusterSetup
import com.flowtick.sysiphos.flow._
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

trait SysiphosApiServer extends SysiphosApi
  with SysiphosApiServerConfig
  with ClusterSetup
  with GraphIQLResources
  with TwitterBootstrapResources
  with UIResources {

  val log: Logger = LoggerFactory.getLogger(getClass)

  val slickExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(instanceThreads))

  lazy val repositoryDataSource: DataSource = dataSource(dbProfile)

  lazy val flowDefinitionRepository: FlowDefinitionRepository = new SlickFlowDefinitionRepository(repositoryDataSource)(dbProfile, slickExecutionContext)
  lazy val flowScheduleRepository: SlickFlowScheduleRepository = new SlickFlowScheduleRepository(repositoryDataSource)(dbProfile, slickExecutionContext)
  lazy val flowInstanceRepository: FlowInstanceRepository = new SlickFlowInstanceRepository(repositoryDataSource)(dbProfile, slickExecutionContext)
  lazy val flowTaskInstanceRepository: FlowTaskInstanceRepository = new SlickFlowTaskInstanceRepository(repositoryDataSource)(dbProfile, slickExecutionContext)

  StaticClusterContext.init(flowScheduleRepository, flowDefinitionRepository, flowInstanceRepository, flowTaskInstanceRepository, flowScheduleRepository)

  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(apiThreads))
  implicit val executorSystem: ActorSystem = ActorSystem(clusterName)

  def apiContext(repositoryContext: RepositoryContext): SysiphosApiContext = new SysiphosApiContext(clusterContext)(executionContext, repositoryContext)

  def clusterContext: ClusterContext = StaticClusterContext.instance.get

  def startExecutorSystem(clusterContext: ClusterContext): Unit = {
    val executorActorProps = Props[FlowExecutorActor](new FlowExecutorActor(
      clusterContext.flowScheduleRepository,
      clusterContext.flowInstanceRepository,
      clusterContext.flowDefinitionRepository,
      clusterContext.flowTaskInstanceRepository,
      clusterContext.flowScheduleStateStore,
      CronScheduler))

    val clusterActors = setupCluster(executorSystem, clusterName, executorActorProps)

    clusterActors.executorSingleton ! Init(clusterActors.workerPool)
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

  def startApiServer(clusterContext: ClusterContext): IO[Unit] = {
    addStatsReporter()

    val startedServer = for {
      _ <- updateDatabase()
      _ <- bindServerToAddress
    } yield startExecutorSystem(clusterContext)

    startedServer.handleErrorWith { error =>
      IO(log.error("unable to start server", error)) *>
        IO(executorSystem.terminate()) *>
        IO(SystemMetrics.stopCollecting()) *>
        IO.raiseError(new RuntimeException(s"unable to start server", error))
    }
  }

}

object SysiphosApiServerApp extends SysiphosApiServer with App {
  startApiServer(clusterContext).unsafeRunSync()
}
