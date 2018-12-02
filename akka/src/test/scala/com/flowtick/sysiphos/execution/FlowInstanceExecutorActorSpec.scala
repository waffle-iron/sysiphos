package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneOffset, ZonedDateTime }

import akka.actor.{ ActorSystem, Props }
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

  trait InstanceExecutorSetup {
    val flowInstanceRepository: FlowInstanceRepository = mock[FlowInstanceRepository]
    val flowTaskInstanceRepository: FlowTaskInstanceRepository = mock[FlowTaskInstanceRepository]

    def flowDefinition: SysiphosDefinition = SysiphosDefinition("ls-definition-id", Seq(CommandLineTask("ls-task-id", None, "ls")))

    lazy val flowInstance = FlowInstanceDetails(
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

    lazy val logger = new ConsoleLogger

    val testEpoch = 42

    implicit val repositoryContext: RepositoryContext = new RepositoryContext {
      override def currentUser: String = "test-user"

      override def epochSeconds: Long = testEpoch
    }

    def flowInstanceActorProps = Props(
      new FlowInstanceExecutorActor(
        flowDefinition,
        flowInstance,
        flowInstanceRepository,
        flowTaskInstanceRepository,
        logger)(repositoryContext) {
        override def now: ZonedDateTime = LocalDateTime.ofEpochSecond(testEpoch, testEpoch, ZoneOffset.UTC).atZone(timeZone)
      })

    lazy val flowExecutorProbe = TestProbe()
    lazy val flowInstanceExecutorActor = TestActorRef(flowInstanceActorProps, flowExecutorProbe.ref)
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "FlowInstanceExecutorActor" should "start the root task on initial execute" in new InstanceExecutorSetup {
    val flowTaskExecutorProbe = TestProbe()

    (flowTaskInstanceRepository.find(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id)), *)
      .returning(Future.successful(Seq.empty))

    (flowTaskInstanceRepository.findOne(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id), taskId = Some(flowDefinition.tasks.head.id)), *)
      .returning(Future.successful(None))

    (flowTaskInstanceRepository.createFlowTaskInstance(_: String, _: String, _: String, _: Int)(_: RepositoryContext))
      .expects(flowInstance.id, *, *, *, *)
      .returning(Future.successful(flowTaskInstance))

    (flowTaskInstanceRepository.setStartTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, testEpoch, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    (flowTaskInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, testEpoch, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Running, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Done, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    (flowInstanceRepository.insertOrUpdateContextValues(_: String, _: Seq[FlowInstanceContextValue])(_: RepositoryContext))
      .expects(flowTaskInstance.flowInstanceId, *, *)
      .returns(Future.successful(Some(flowInstance)))

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute(PendingTasks)

    flowExecutorProbe.fishForMessage(max = 10.seconds) {
      case TaskCompleted(_) => true
      case _ => false
    }
  }

  it should s"not enqueue execute task with existing instance in status ${FlowTaskInstanceStatus.New}" in new InstanceExecutorSetup {
    (flowTaskInstanceRepository.find(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id)), *)
      .returning(Future.successful(Seq(flowTaskInstance.copy(status = FlowTaskInstanceStatus.New))))

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute(PendingTasks)

    flowExecutorProbe.fishForMessage(max = 10.seconds) {
      case WorkPending(_) => true
      case _ => false
    }
  }

  it should s"not execute task with status retry before due date" in new InstanceExecutorSetup {
    val taskInstanceInRetry: FlowTaskInstanceDetails = flowTaskInstance.copy(
      status = FlowTaskInstanceStatus.Retry,
      nextDueDate = Some(1000))

    (flowTaskInstanceRepository.find(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id)), *)
      .returning(Future.successful(Seq(taskInstanceInRetry)))

    (flowTaskInstanceRepository.findOne(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id), taskId = Some(taskInstanceInRetry.taskId)), *)
      .returning(Future.successful(Some(taskInstanceInRetry)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstance.id, *, *)
      .returns(Future.successful(Some(taskInstanceInRetry)))
      .never()

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute(TaskId(taskInstanceInRetry.taskId))

    flowExecutorProbe.fishForMessage(max = 10.seconds) {
      case WorkPending(_) => true
      case _ => false
    }

    flowExecutorProbe.expectNoMessage()
  }

  it should "set due date for retry on failure with retries left" in new InstanceExecutorSetup {
    val flowInstanceExecutorProbe = TestProbe()
    val flowTaskExecutorProbe = TestProbe()
    val flowTaskInstanceWithRetry: FlowTaskInstanceDetails = flowTaskInstance.copy(retries = 1)

    (flowTaskInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstanceWithRetry.id, testEpoch, *)
      .returns(Future.successful(Some(flowTaskInstanceWithRetry)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstanceWithRetry.id, FlowTaskInstanceStatus.Retry, *)
      .returns(Future.successful(Some(flowTaskInstanceWithRetry)))

    (flowTaskInstanceRepository.setRetries(_: String, _: Int)(_: RepositoryContext))
      .expects(flowTaskInstanceWithRetry.id, 0, *)
      .returns(Future.successful(Some(flowTaskInstanceWithRetry)))

    (flowTaskInstanceRepository.setNextDueDate(_: String, _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstanceWithRetry.id, Some(18000L + testEpoch), *)
      .returns(Future.successful(Some(flowTaskInstanceWithRetry)))

    flowInstanceExecutorActor ! WorkFailed(new RuntimeException("error"), flowTaskInstanceWithRetry)

    flowExecutorProbe.expectMsg(RetryScheduled(flowTaskInstanceWithRetry))
  }

  it should "execute task in retry on explicit execute" in new InstanceExecutorSetup {
    val flowInstanceExecutorProbe = TestProbe()
    val flowTaskExecutorProbe = TestProbe()

    val taskInstanceInRetry: FlowTaskInstanceDetails = flowTaskInstance.copy(status = FlowTaskInstanceStatus.Retry)

    (flowTaskInstanceRepository.find(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id)), *)
      .returning(Future.successful(Seq(taskInstanceInRetry)))
      .noMoreThanTwice()

    (flowTaskInstanceRepository.findOne(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id), taskId = Some(flowDefinition.tasks.head.id)), *)
      .returning(Future.successful(Some(taskInstanceInRetry)))
      .noMoreThanTwice()

    (flowTaskInstanceRepository.setStartTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, testEpoch, *)
      .returning(Future.successful(Some(taskInstanceInRetry)))
      .noMoreThanTwice()

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Running, *)
      .returns(Future.successful(Some(taskInstanceInRetry)))
      .noMoreThanTwice()

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Done, *)
      .returns(Future.successful(Some(taskInstanceInRetry)))
      .noMoreThanTwice()

    (flowTaskInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, testEpoch, *)
      .returns(Future.successful(Some(taskInstanceInRetry)))
      .noMoreThanTwice()

    (flowInstanceRepository.insertOrUpdateContextValues(_: String, _: Seq[FlowInstanceContextValue])(_: RepositoryContext))
      .expects(flowTaskInstance.flowInstanceId, *, *)
      .returns(Future.successful(Some(flowInstance)))

    // pending tasks execution, should not execute retry
    flowInstanceExecutorActor ! Execute(PendingTasks)

    flowExecutorProbe.expectMsg(WorkPending(FlowInstanceDetails("???", "ls-definition-id", 1, None, None, FlowInstanceStatus.Scheduled, List())))

    // explicit execute, should execute retry
    flowInstanceExecutorActor ! Execute(TaskId(flowDefinition.tasks.head.id))

    flowExecutorProbe.expectMsg(WorkPending(FlowInstanceDetails("???", "ls-definition-id", 1, None, None, FlowInstanceStatus.Scheduled, List())))
    flowExecutorProbe.expectMsg(TaskCompleted(taskInstanceInRetry))
  }

  it should "notify about failure when retries are exhausted" in new InstanceExecutorSetup {
    val flowInstanceExecutorProbe = TestProbe()
    val flowTaskExecutorProbe = TestProbe()

    val taskInstanceWithoutRetries: FlowTaskInstanceDetails = flowTaskInstance.copy(retries = 0)

    (flowTaskInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, testEpoch, *)
      .returns(Future.successful(Some(taskInstanceWithoutRetries)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Failed, *)
      .returns(Future.successful(Some(taskInstanceWithoutRetries)))

    flowInstanceExecutorActor ! WorkFailed(new RuntimeException("error"), taskInstanceWithoutRetries)

    flowExecutorProbe.expectMsg(ExecutionFailed(flowInstance))
  }

  it should "throttle task execution" in new InstanceExecutorSetup {
    override def flowDefinition: SysiphosDefinition = super.flowDefinition.copy(
      tasks = manyCommands,
      taskParallelism = Some(50))

    val manyCommands = Seq.tabulate(100)(index => CommandLineTask(s"ls-task-id-$index", None, "ls"))

    (flowTaskInstanceRepository.find(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id)), *)
      .returning(Future.successful(Seq.empty))
      .atLeastOnce()

    (flowTaskInstanceRepository.findOne(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(*, *)
      .returning(Future.successful(None))
      .atLeastOnce()

    (flowTaskInstanceRepository.createFlowTaskInstance(_: String, _: String, _: String, _: Int)(_: RepositoryContext))
      .expects(flowInstance.id, *, *, *, *)
      .returning(Future.successful(flowTaskInstance))
      .atLeastOnce()

    (flowTaskInstanceRepository.setStartTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, testEpoch, *)
      .returning(Future.successful(Some(flowTaskInstance)))
      .atLeastOnce()

    (flowTaskInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, testEpoch, *)
      .returning(Future.successful(Some(flowTaskInstance)))
      .atLeastOnce()

    (flowInstanceRepository.insertOrUpdateContextValues(_: String, _: Seq[FlowInstanceContextValue])(_: RepositoryContext))
      .expects(flowTaskInstance.flowInstanceId, *, *)
      .returns(Future.successful(Some(flowInstance)))
      .atLeastOnce()

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Running, *)
      .returning(Future.successful(Some(flowTaskInstance)))
      .atLeastOnce()

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus)(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Done, *)
      .returning(Future.successful(Some(flowTaskInstance)))
      .atLeastOnce()

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute(PendingTasks)

    val allInTenSeconds: Int = flowExecutorProbe.receiveWhile(10.seconds) {
      case TaskCompleted(_) => 1
      case _ => 0
    }.sum

    allInTenSeconds should be >= 2
    allInTenSeconds should be <= 10
  }
}
