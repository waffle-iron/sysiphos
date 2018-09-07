package com.flowtick.sysiphos.execution

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit, TestProbe }
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.task.CommandLineTask
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }

import scala.concurrent.Future

class FlowInstanceExecutorActorSpec extends TestKit(ActorSystem("MySpec")) with ImplicitSender with MockFactory
  with FlatSpecLike with Matchers with BeforeAndAfterAll {

  val flowInstanceRepository: FlowInstanceRepository = stub[FlowInstanceRepository]
  val flowTaskInstanceRepository: FlowTaskInstanceRepository = stub[FlowTaskInstanceRepository]

  val flowDefinition: FlowDefinition = SysiphosDefinition("ls-definition-id", CommandLineTask("ls-task-id", None, "ls"))

  val flowInstance: FlowInstance = FlowInstanceDetails(
    status = FlowInstanceStatus.New,
    id = "???",
    flowDefinitionId = flowDefinition.id,
    creationTime = 1L,
    retries = 3,
    context = Seq.empty,
    startTime = None,
    endTime = None)

  lazy val flowTaskInstance = FlowTaskInstanceDetails(
    id = "task-id",
    flowInstanceId = flowInstance.id,
    taskId = flowDefinition.task.id,
    creationTime = 1l,
    startTime = None,
    endTime = None,
    retries = 0,
    status = FlowTaskInstanceStatus.New)

  implicit val repositoryContext: RepositoryContext = new RepositoryContext {
    override def currentUser: String = "test-user"
  }

  val flowInstanceActorProps = Props(
    new FlowInstanceExecutorActor(
      flowDefinition,
      flowInstance,
      flowInstanceRepository,
      flowTaskInstanceRepository)(repositoryContext))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "FlowInstanceExecutorActor" should "create on start task definition list" in {
    val flowInstanceExecutorActor = TestActorRef.apply[FlowInstanceExecutorActor](flowInstanceActorProps)
    (flowTaskInstanceRepository.getFlowTaskInstances(_: String)(_: RepositoryContext))
      .when(flowInstance.id, *)
      .returns(Future.successful(Seq(flowTaskInstance)))

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute(flowDefinition)
  }

  it should "send proper message to flow task executor" in {

    val flowTaskExecutorProbe = TestProbe()

    val flowInstanceExecutorActor = TestActorRef(
      new FlowInstanceExecutorActor(flowDefinition, flowInstance, flowInstanceRepository, flowTaskInstanceRepository) {
        override def flowTaskExecutor: ActorRef = flowTaskExecutorProbe.ref
      })

    (flowTaskInstanceRepository.getFlowTaskInstances(_: String)(_: RepositoryContext))
      .when(flowInstance.id, *)
      .returns(Future.successful(Seq(flowTaskInstance)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .when(flowTaskInstance.id, FlowTaskInstanceStatus.Running, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute(flowDefinition)

    flowTaskExecutorProbe.expectMsg(FlowTaskExecution.Execute(flowDefinition.task, flowTaskInstance))

  }
}
