package com.flowtick.sysiphos.execution

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.flow.FlowInstanceStatus.FlowInstanceStatus
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler.{ FlowScheduleRepository, FlowScheduleStateStore, FlowScheduler }
import com.flowtick.sysiphos.task.CommandLineTask
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

class FlowExecutorActorSpec extends TestKit(ActorSystem("MySpec"))
  with ImplicitSender
  with FlatSpecLike
  with FlowExecution
  with MockFactory
  with Matchers
  with BeforeAndAfterAll {

  val flowDefinition: FlowDefinition = SysiphosDefinition(
    "ls-definition-id",
    CommandLineTask("ls-task-id", None, "ls"),
    latestOnly = true)

  val flowInstance = FlowInstanceDetails(
    status = FlowInstanceStatus.Scheduled,
    id = "???",
    flowDefinitionId = flowDefinition.id,
    creationTime = 1L,
    context = Seq.empty,
    startTime = None,
    endTime = None)

  "FlowExecutionActor" should "in case of lastOnly run only last instance with same context" in {
    Seq(flowInstance, flowInstance.copy(creationTime = 2L)).map { flowInstance =>
      (flowInstanceRepository.setStatus(_: String, _: FlowInstanceStatus)(_: RepositoryContext))
        .expects(flowInstance.id, FlowInstanceStatus.Skipped, *)
        .returning(Future.successful(Option(flowInstance)))
    }

    val latestInstances = latestOnly(
      flowDefinition,
      Seq(flowInstance, flowInstance.copy(creationTime = 2L), flowInstance.copy(creationTime = 3L)))

    Await.result(latestInstances, Duration.Inf) should be(Seq(FlowInstanceDetails("???", "ls-definition-id", 3, None, None, FlowInstanceStatus.Scheduled, Seq.empty)))
  }

  override val flowScheduleRepository: FlowScheduleRepository = mock[FlowScheduleRepository]
  override val flowDefinitionRepository: FlowDefinitionRepository = mock[FlowDefinitionRepository]
  override val flowInstanceRepository: FlowInstanceRepository = mock[FlowInstanceRepository]
  override val flowScheduleStateStore: FlowScheduleStateStore = mock[FlowScheduleStateStore]
  override val flowScheduler: FlowScheduler = mock[FlowScheduler]
  override val flowTaskInstanceRepository: FlowTaskInstanceRepository = mock[FlowTaskInstanceRepository]
  override implicit val repositoryContext: RepositoryContext = mock[RepositoryContext]

  override def executeInstance(instance: FlowInstance, selectedTaskId: Option[String]): Future[FlowInstance] = {
    Future.successful(instance)
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  override implicit val executionContext: ExecutionContext = ExecutionContext.global

  override def executeRunning(
    running: FlowInstanceDetails,
    definition: FlowDefinition,
    selectedTaskId: Option[String]): Future[Any] = Future.successful(running)
}
