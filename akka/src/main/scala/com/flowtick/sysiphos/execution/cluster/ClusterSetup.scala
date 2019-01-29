package com.flowtick.sysiphos.execution.cluster

import java.net.URI

import akka.actor.{ ActorRef, ActorSystem, Address, PoisonPill, Props }
import akka.cluster.Cluster
import akka.cluster.routing.{ ClusterRouterPool, ClusterRouterPoolSettings }
import akka.cluster.singleton.{ ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings }
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.routing.RoundRobinPool
import com.flowtick.sysiphos.config.Configuration

final case class ClusterActors(executorSingleton: ActorRef, workerPool: ActorRef)

trait ClusterSetup {
  def setupCluster(
    system: ActorSystem,
    clusterName: String,
    executorActorProps: Props): ClusterActors = {
    bootstrapCluster(system, clusterName)

    system.actorOf(Props[ClusterListener], name = "clusterListener")

    val executorActorRef: ActorRef = createExecutorSingleton(system, executorActorProps)
    val workerPoolRef: ActorRef = createWorkerPool(system, executorActorRef)

    ClusterActors(executorActorRef, workerPoolRef)
  }

  private def bootstrapCluster(system: ActorSystem, clusterName: String): Unit = {
    if (Configuration.propOrEnv("clustering.enabled").getOrElse("false").toBoolean) {
      // use configured discovery mechanism to form cluster:
      // https://developer.lightbend.com/docs/akka-management/current/bootstrap/index.html
      AkkaManagement(system).start()

      ClusterBootstrap(system).start()
    } else {
      val defaultClusterAddress = Configuration.propOrEnv("clustering.ip", "127.0.0.1")
      val defaultClusterPort = Configuration.propOrEnv("clustering.port", "1600")

      // per default join self to form standalone cluster without discovery (single master)
      val seedNodes: Array[Address] = Configuration.propOrEnv("akka.cluster.seed-nodes")
        .getOrElse(s"akka.tcp://$clusterName@$defaultClusterAddress:$defaultClusterPort").split(",")
        .map(addressString => {
          val uri = new URI(addressString)
          Address(uri.getScheme, uri.getUserInfo, uri.getHost, uri.getPort)
        })

      Cluster(system).joinSeedNodes(scala.collection.immutable.Seq(seedNodes: _*))
    }
  }

  private def createWorkerPool(system: ActorSystem, executorActorRef: ActorRef): ActorRef = {
    val clusterRouterPoolSettings = ClusterRouterPoolSettings(
      totalInstances = 10,
      allowLocalRoutees = true,
      maxInstancesPerNode = 2,
      useRoles = Set.empty[String])

    val routeeProps = Props(new ClusterWorkerActor(flowExecutorActor = executorActorRef))
    val clusterPoolProps = ClusterRouterPool(RoundRobinPool(10), clusterRouterPoolSettings).props(routeeProps)

    system.actorOf(clusterPoolProps)
  }

  private def createExecutorSingleton(system: ActorSystem, executorActorProps: Props): ActorRef = {
    val executorSingletonManagerProps = ClusterSingletonManager.props(
      singletonProps = executorActorProps,
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system))

    val executorSingletonManager = system.actorOf(executorSingletonManagerProps, name = "executor")

    val executorSingletonProps = ClusterSingletonProxy.props(
      singletonManagerPath = executorSingletonManager.path.toStringWithoutAddress,
      settings = ClusterSingletonProxySettings(system))

    system.actorOf(executorSingletonProps, name = "executorProxy")
  }
}
