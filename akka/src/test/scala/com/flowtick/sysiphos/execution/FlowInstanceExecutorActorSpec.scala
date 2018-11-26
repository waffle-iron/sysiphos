package com.flowtick.sysiphos.execution

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit, TestProbe }
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowInstanceExecution._
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.flow.FlowTaskInstanceStatus.FlowTaskInstanceStatus
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.logging.ConsoleLogger
import com.flowtick.sysiphos.task.CommandLineTask
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }

import scala.concurrent.Future
import scala.concurrent.duration._

class FlowInstanceExecutorActorSpec extends TestKit(ActorSystem("instance-executor-spec"))
  with ImplicitSender
  with MockFactory
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll {

  trait ExecutorActorSpec {
    val flowInstanceRepository: FlowInstanceRepository = mock[FlowInstanceRepository]
    val flowTaskInstanceRepository: FlowTaskInstanceRepository = mock[FlowTaskInstanceRepository]

    val flowDefinition: FlowDefinition = SysiphosDefinition("ls-definition-id", Seq(CommandLineTask("ls-task-id", None, "ls")))

    val flowInstance = FlowInstanceDetails(
      status = FlowInstanceStatus.Scheduled,
      id = "???",
      flowDefinitionId = flowDefinition.id,
      creationTime = 1L,
      context = Seq.empty,
      startTime = None,
      endTime = None)

    lazy val flowTaskInstance = FlowTaskInstanceDetails(
      id = "task-id",
      flowInstanceId = flowInstance.id,
      taskId = flowDefinition.tasks.head.id,
      creationTime = 1l,
      startTime = None,
      endTime = None,
      retries = 0,
      status = FlowTaskInstanceStatus.New,
      retryDelay = None,
      nextDueDate = None,
      logId = "log-id")

    val logger = new ConsoleLogger

    implicit val repositoryContext: RepositoryContext = new RepositoryContext {
      override def currentUser: String = "test-user"

      override def epochSeconds: Long = 0
    }

    def flowInstanceActorProps = Props(
      new FlowInstanceExecutorActor(
        flowDefinition,
        flowInstance,
        flowInstanceRepository,
        flowTaskInstanceRepository,
        logger)(repositoryContext))

  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "FlowInstanceExecutorActor" should "execute task with existing instance" in new ExecutorActorSpec {
    val flowExecutorProbe = TestProbe()
    val flowTaskExecutorProbe = TestProbe()

    val flowInstanceExecutorActor = TestActorRef(
      Props(new FlowInstanceExecutorActor(flowDefinition, flowInstance, flowInstanceRepository, flowTaskInstanceRepository, logger) {
        override def flowTaskExecutor(taskInstance: FlowTaskInstance) = flowTaskExecutorProbe.ref
      }), flowExecutorProbe.ref)

    (flowTaskInstanceRepository.getFlowTaskInstances(_: Option[String], _: Option[Long], _: Option[Seq[FlowTaskInstanceStatus.FlowTaskInstanceStatus]])(_: RepositoryContext))
      .expects(Some(flowInstance.id), None, None, *)
      .returning(Future.successful(Seq(flowTaskInstance)))

    (flowTaskInstanceRepository.setStartTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, 0, *)
      .returning(Future.successful(Some(flowTaskInstance)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Running, *)
      .returning(Future.successful(Some(flowTaskInstance)))

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute(None)

    flowTaskExecutorProbe.expectMsgPF(max = 10.seconds) {
      case FlowTaskExecution.Execute(task, instance) => true
    }

    flowExecutorProbe.expectMsgPF(max = 10.seconds) {
      case WorkTriggered(tasks) if tasks.head.flowTask == flowDefinition.tasks.head => true
    }
  }

  it should "start the root task on initial execute" in new ExecutorActorSpec {

    val flowTaskExecutorProbe = TestProbe()

    val flowInstanceExecutorActor = TestActorRef(
      new FlowInstanceExecutorActor(flowDefinition, flowInstance, flowInstanceRepository, flowTaskInstanceRepository, logger) {
        override def flowTaskExecutor(taskInstance: FlowTaskInstance) = flowTaskExecutorProbe.ref
      })

    (flowTaskInstanceRepository.getFlowTaskInstances(_: Option[String], _: Option[Long], _: Option[Seq[FlowTaskInstanceStatus]])(_: RepositoryContext))
      .expects(Some(flowInstance.id), None, None, *)
      .returns(Future.successful(Seq(flowTaskInstance)))

    (flowTaskInstanceRepository.setStartTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, 0, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Running, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute(None)

    flowTaskExecutorProbe.expectMsgPF() {
      case FlowTaskExecution.Execute(_, _) => true
    }
  }

  it should "update status on work done and execute the next task" in new ExecutorActorSpec {
    val flowExecutorProbe = TestProbe()
    val flowInstanceExecutorProbe = TestProbe()
    val flowTaskExecutorProbe = TestProbe()

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Done, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    (flowTaskInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, 0, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    val flowInstanceProbeActorProps = Props(
      new FlowInstanceExecutorActor(flowDefinition, flowInstance, flowInstanceRepository, flowTaskInstanceRepository, logger) {
        override def flowTaskExecutor(taskInstance: FlowTaskInstance): ActorRef = flowTaskExecutorProbe.ref
        override def selfRef: ActorRef = flowInstanceExecutorProbe.ref
      })

    val flowInstanceExecutorActor = TestActorRef(flowInstanceProbeActorProps, flowExecutorProbe.ref)

    flowInstanceExecutorActor ! WorkDone(flowTaskInstance)
    flowInstanceExecutorProbe.expectMsg(FlowInstanceExecution.Execute(None))
  }

  it should "set due date for retry on failure with retries left" in new ExecutorActorSpec {
    val flowExecutorProbe = TestProbe()
    val flowInstanceExecutorProbe = TestProbe()
    val flowTaskExecutorProbe = TestProbe()
    val flowTaskInstanceWithRetry = flowTaskInstance.copy(retries = 1)

    val flowInstanceProbeActorProps = Props(
      new FlowInstanceExecutorActor(flowDefinition, flowInstance, flowInstanceRepository, flowTaskInstanceRepository, logger) {
        override def flowTaskExecutor(taskInstance: FlowTaskInstance): ActorRef = flowTaskExecutorProbe.ref
        override def selfRef: ActorRef = flowInstanceExecutorProbe.ref
      })

    val flowInstanceExecutorActor = TestActorRef(flowInstanceProbeActorProps, flowExecutorProbe.ref)

    (flowTaskInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstanceWithRetry.id, 0, *)
      .returns(Future.successful(Some(flowTaskInstanceWithRetry)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstanceWithRetry.id, FlowTaskInstanceStatus.Retry, *)
      .returns(Future.successful(Some(flowTaskInstanceWithRetry)))

    (flowTaskInstanceRepository.setRetries(_: String, _: Int)(_: RepositoryContext))
      .expects(flowTaskInstanceWithRetry.id, 0, *)
      .returns(Future.successful(Some(flowTaskInstanceWithRetry)))

    (flowTaskInstanceRepository.setNextDueDate(_: String, _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstanceWithRetry.id, Some(18000L), *)
      .returns(Future.successful(Some(flowTaskInstanceWithRetry)))

    flowInstanceExecutorActor ! WorkFailed(new RuntimeException("error"), flowTaskInstanceWithRetry)

    flowExecutorProbe.expectMsg(RetryScheduled(flowTaskInstanceWithRetry))
  }

  it should "notify about failure when retries are exhausted" in new ExecutorActorSpec {
    val flowExecutorProbe = TestProbe()
    val flowInstanceExecutorProbe = TestProbe()
    val flowTaskExecutorProbe = TestProbe()

    val flowInstanceProbeActorProps = Props(
      new FlowInstanceExecutorActor(flowDefinition, flowInstance, flowInstanceRepository, flowTaskInstanceRepository, logger) {
        override def flowTaskExecutor(taskInstance: FlowTaskInstance): ActorRef = flowTaskExecutorProbe.ref
        override def selfRef: ActorRef = flowInstanceExecutorProbe.ref
      })

    val flowInstanceExecutorActor = TestActorRef(flowInstanceProbeActorProps, flowExecutorProbe.ref)

    (flowTaskInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, 0, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Failed, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    flowInstanceExecutorActor ! WorkFailed(new RuntimeException("error"), flowTaskInstance)

    flowExecutorProbe.expectMsg(ExecutionFailed(flowInstance))
  }
}
