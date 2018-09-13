package com.flowtick.sysiphos.execution

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit, TestProbe }
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowInstanceExecution.{ ExecutionFailed, Retry, WorkDone, WorkFailed }
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
    status = FlowTaskInstanceStatus.New,
    retryDelay = None,
    nextDueDate = None)

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

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute
  }

  it should "start the root task on initial execute" in {

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

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute

    flowTaskExecutorProbe.expectMsg(FlowTaskExecution.Execute(flowDefinition.task, flowTaskInstance))
  }

  it should "update status on work done and execute the next task" in {
    val flowExecutorProbe = TestProbe()
    val flowInstanceExecutorProbe = TestProbe()
    val flowTaskExecutorProbe = TestProbe()

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .when(flowTaskInstance.id, FlowTaskInstanceStatus.Done, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    val flowInstanceActorProps = Props(
      new FlowInstanceExecutorActor(flowDefinition, flowInstance, flowInstanceRepository, flowTaskInstanceRepository) {
        override def flowTaskExecutor: ActorRef = flowTaskExecutorProbe.ref
        override def selfRef: ActorRef = flowInstanceExecutorProbe.ref
      })

    val flowInstanceExecutorActor = TestActorRef(flowInstanceActorProps, flowExecutorProbe.ref)

    flowInstanceExecutorActor ! WorkDone(flowTaskInstance)
    flowInstanceExecutorProbe.expectMsg(FlowInstanceExecution.Execute)
  }

  it should "ask parent for retry of a task" in {
    val flowExecutorProbe = TestProbe()
    val flowInstanceExecutorProbe = TestProbe()
    val flowTaskExecutorProbe = TestProbe()
    val flowTaskInstanceWithRetry = flowTaskInstance.copy(retries = 1)

    val flowInstanceActorProps = Props(
      new FlowInstanceExecutorActor(flowDefinition, flowInstance, flowInstanceRepository, flowTaskInstanceRepository) {
        override def flowTaskExecutor: ActorRef = flowTaskExecutorProbe.ref
        override def selfRef: ActorRef = flowInstanceExecutorProbe.ref
      })

    val flowInstanceExecutorActor = TestActorRef(flowInstanceActorProps, flowExecutorProbe.ref)

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .when(flowTaskInstanceWithRetry.id, FlowTaskInstanceStatus.Failed, *)
      .returns(Future.successful(Some(flowTaskInstanceWithRetry)))

    (flowInstanceRepository.setStatus(_: String, _: FlowInstanceStatus.FlowInstanceStatus)(_: RepositoryContext))
      .when(flowTaskInstanceWithRetry.id, FlowInstanceStatus.Failed, *)
      .returns(Future.successful(()))

    (flowTaskInstanceRepository.setRetries(_: String, _: Int)(_: RepositoryContext))
      .when(flowTaskInstanceWithRetry.id, flowTaskInstanceWithRetry.retries - 1, *)
      .returns(Future.successful(Some(flowTaskInstanceWithRetry.copy(retries = 0))))

    flowInstanceExecutorActor ! WorkFailed(new RuntimeException("error"), flowDefinition.task, flowTaskInstanceWithRetry)

    flowExecutorProbe.expectMsg(Retry(flowDefinition.task, flowTaskInstanceWithRetry))
  }

  it should "notify about failure" in {
    val flowExecutorProbe = TestProbe()
    val flowInstanceExecutorProbe = TestProbe()
    val flowTaskExecutorProbe = TestProbe()

    val flowInstanceActorProps = Props(
      new FlowInstanceExecutorActor(flowDefinition, flowInstance, flowInstanceRepository, flowTaskInstanceRepository) {
        override def flowTaskExecutor: ActorRef = flowTaskExecutorProbe.ref
        override def selfRef: ActorRef = flowInstanceExecutorProbe.ref
      })

    val flowInstanceExecutorActor = TestActorRef(flowInstanceActorProps, flowExecutorProbe.ref)

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .when(flowTaskInstance.id, FlowTaskInstanceStatus.Failed, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    (flowInstanceRepository.setStatus(_: String, _: FlowInstanceStatus.FlowInstanceStatus)(_: RepositoryContext))
      .when(flowTaskInstance.id, FlowInstanceStatus.Failed, *)
      .returns(Future.successful(()))

    flowInstanceExecutorActor ! WorkFailed(new RuntimeException("error"), flowDefinition.task, flowTaskInstance)

    flowExecutorProbe.expectMsg(ExecutionFailed(flowTaskInstance))
  }
}
