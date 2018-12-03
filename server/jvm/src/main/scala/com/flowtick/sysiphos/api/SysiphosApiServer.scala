package com.flowtick.sysiphos.api

import java.util.concurrent.Executors

import akka.actor.{ ActorSystem, Props }
import com.flowtick.sysiphos.api.resources.{ GraphIQLResources, TwitterBootstrapResources, UIResources }
import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowExecutorActor.Init
import com.flowtick.sysiphos.execution.{ CronScheduler, FlowExecutorActor }
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler._
import com.flowtick.sysiphos.slick._
import com.twitter.finagle.core.util.InetAddressUtil
import com.twitter.finagle.{ Http, ListeningServer }
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import io.finch.Application
import io.finch.circe._
import kamon.Kamon
import kamon.metric.PeriodSnapshot
import kamon.prometheus.PrometheusReporter
import kamon.statsd.StatsDReporter
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

  def bindServerToAddress: Try[ListeningServer] = Try {
    val logo = scala.io.Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("logo.txt")).getLines().mkString("\n")
    log.info(s"starting ...\n$logo")

    val address = s"$bindAddress:$httpPort"

    val service = (api :+: graphiqlResources :+: bootstrapResources :+: uiResources).toServiceAs[Application.Json]
    val server = Http.server.serve(address, service)

    log.info(s"running at ${server.boundAddress.toString}")

    server
  }

  def updateDatabase(): Try[Unit] = Try {
    log.info(s"using database profile $dbProfileName for migrations: $dbUrl")

    DefaultSlickRepositoryMigrations.updateDatabase(dataSource(dbProfile))
  }.flatten

  def addStatsReporter(): Unit = {
    if (Configuration.propOrEnv("stats.enabled", "false").toBoolean) {
      log.info("adding stats reporters...")
      val statsDReporter = new StatsDReporter() {
        override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
          log.info(s"writing snapshot $snapshot")
          super.reportPeriodSnapshot(snapshot)
        }
      }

      val baseConfig = ConfigFactory.load()

      val config: Option[Config] = for {
        statsHost <- Configuration.propOrEnv("stats.host")
        statsPort <- Configuration.propOrEnv("stats.port").map(_.toInt).orElse(Some(8125))
      } yield {
        val resolvedStatsHost = InetAddressUtil.getByName(statsHost)

        log.info(s"using stats host: $statsHost (${resolvedStatsHost.getHostAddress})")
        log.info(s"using stats port: $statsPort")

        baseConfig
          .withValue("kamon.statsd.hostname", ConfigValueFactory.fromAnyRef(resolvedStatsHost.getHostAddress))
          .withValue("kamon.statsd.port", ConfigValueFactory.fromAnyRef(statsPort))
      }

      statsDReporter.reconfigure(config.getOrElse(baseConfig))
      Kamon.addReporter(statsDReporter)
      Kamon.addReporter(new PrometheusReporter)
    }
  }

  def startApiServer(): Unit = {
    addStatsReporter()

    val listeningServer = for {
      _ <- updateDatabase()
      listening <- bindServerToAddress
    } yield listening

    listeningServer.recoverWith {
      case error => Failure(new RuntimeException(s"unable to start server", error))
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

  lazy val flowDefinitionRepository: FlowDefinitionRepository = new SlickFlowDefinitionRepository(dataSource(dbProfile))(dbProfile, slickExecutionContext)
  lazy val flowScheduleRepository: SlickFlowScheduleRepository = new SlickFlowScheduleRepository(dataSource(dbProfile))(dbProfile, slickExecutionContext)
  lazy val flowInstanceRepository: FlowInstanceRepository = new SlickFlowInstanceRepository(dataSource(dbProfile))(dbProfile, slickExecutionContext)
  lazy val flowTaskInstanceRepository: FlowTaskInstanceRepository = new SlickFlowTaskInstanceRepository(dataSource(dbProfile))(dbProfile, slickExecutionContext)

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit val executorSystem: ActorSystem = ActorSystem()
  implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

  def apiContext(repositoryContext: RepositoryContext) = new SysiphosApiContext(
    flowDefinitionRepository,
    flowScheduleRepository,
    flowInstanceRepository,
    flowScheduleRepository,
    flowTaskInstanceRepository)(apiExecutionContext, repositoryContext)

  startApiServer()
}
