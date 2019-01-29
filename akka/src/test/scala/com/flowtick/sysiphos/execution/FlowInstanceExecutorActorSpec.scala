package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneOffset, ZonedDateTime }

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit, TestProbe }
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowInstanceExecution._
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
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

  class InstanceExecutorSetup(val flowDefinition: FlowDefinition = SysiphosDefinition("ls-definition-id", Seq(CommandLineTask("ls-task-id", None, "ls")))) {
    val flowDefinitionRepository: FlowDefinitionRepository = mock[FlowDefinitionRepository]
    val flowInstanceRepository: FlowInstanceRepository = mock[FlowInstanceRepository]
    val flowTaskInstanceRepository: FlowTaskInstanceRepository = mock[FlowTaskInstanceRepository]

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
      flowDefinitionId = flowDefinition.id,
      taskId = flowDefinition.tasks.head.id,
      creationTime = 1l,
      startTime = None,
      endTime = None,
      retries = 0,
      status = FlowTaskInstanceStatus.New,
      retryDelay = 10,
      nextDueDate = None,
      logId = "log-id")

    lazy val logger = new ConsoleLogger

    val testEpoch = 42

    implicit val repositoryContext: RepositoryContext = new RepositoryContext {
      override def currentUser: String = "test-user"

      override def epochSeconds: Long = testEpoch
    }

    lazy val flowExecutorProbe = TestProbe()
    lazy val flowInstanceExecutorActor = TestActorRef(flowInstanceActorProps)

    def flowInstanceActorProps = Props(
      new FlowInstanceExecutorActor(
        DefaultClusterContext(flowDefinitionRepository = flowDefinitionRepository, flowInstanceRepository = flowInstanceRepository, flowTaskInstanceRepository = flowTaskInstanceRepository, flowScheduleRepository = null, flowScheduleStateStore = null),
        flowExecutorProbe.ref,
        logger)(repositoryContext) {
        override def currentTime: ZonedDateTime = LocalDateTime.ofEpochSecond(testEpoch, testEpoch, ZoneOffset.UTC).atZone(timeZone)
      })

    (flowDefinitionRepository.findById(_: String)(_: RepositoryContext))
      .expects("ls-definition-id", *)
      .returning(Future.successful(Option(FlowDefinitionDetails(
        flowDefinition.id,
        None,
        Some(FlowDefinition.toJson(flowDefinition)),
        Some(0))))).anyNumberOfTimes()
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "FlowInstanceExecutorActor" should "start the root task on initial execute" in new InstanceExecutorSetup() {
    val flowTaskExecutorProbe = TestProbe()

    (flowInstanceRepository.findById(_: String)(_: RepositoryContext))
      .expects(*, *)
      .returning(Future.successful(Some(flowInstance)))

    (flowTaskInstanceRepository.find(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id)), *)
      .returning(Future.successful(Seq.empty))

    (flowTaskInstanceRepository.findOne(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id), taskId = Some(flowDefinition.tasks.head.id)), *)
      .returning(Future.successful(None))

    (flowTaskInstanceRepository.createFlowTaskInstance(_: String, _: String, _: String, _: String, _: Int, _: Long, _: Option[Long], _: Option[FlowTaskInstanceStatus.FlowTaskInstanceStatus])(_: RepositoryContext))
      .expects(flowInstance.id, *, *, *, *, *, *, *, *)
      .returning(Future.successful(flowTaskInstance))

    (flowTaskInstanceRepository.setStartTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, testEpoch, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    (flowTaskInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, testEpoch, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus, _: Option[Int], _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Running, *, *, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus, _: Option[Int], _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Done, *, *, *)
      .returns(Future.successful(Some(flowTaskInstance)))

    (flowInstanceRepository.insertOrUpdateContextValues(_: String, _: Seq[FlowInstanceContextValue])(_: RepositoryContext))
      .expects(flowTaskInstance.flowInstanceId, *, *)
      .returns(Future.successful(Some(flowInstance)))

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute(flowInstance, PendingTasks)

    flowExecutorProbe.fishForMessage(max = 10.seconds) {
      case TaskCompleted(_) => true
      case _ => false
    }
  }

  it should s"not enqueue execute task with existing instance in status ${FlowTaskInstanceStatus.New}" in new InstanceExecutorSetup {
    (flowTaskInstanceRepository.find(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id)), *)
      .returning(Future.successful(Seq(flowTaskInstance.copy(status = FlowTaskInstanceStatus.New))))

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute(flowInstance, PendingTasks)

    flowExecutorProbe.fishForMessage(max = 10.seconds) {
      case WorkPending(_) => true
      case _ => false
    }
  }

  it should s"not execute task with status retry before due date" in new InstanceExecutorSetup {
    val taskInstanceInRetry: FlowTaskInstanceDetails = flowTaskInstance.copy(
      status = FlowTaskInstanceStatus.Retry,
      nextDueDate = Some(1000))

    (flowInstanceRepository.findById(_: String)(_: RepositoryContext))
      .expects(*, *)
      .returning(Future.successful(Some(flowInstance)))

    (flowTaskInstanceRepository.find(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id)), *)
      .returning(Future.successful(Seq(taskInstanceInRetry)))

    (flowTaskInstanceRepository.findOne(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id), taskId = Some(taskInstanceInRetry.taskId)), *)
      .returning(Future.successful(Some(taskInstanceInRetry)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus, _: Option[Int], _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstance.id, *, *, *, *)
      .returns(Future.successful(Some(taskInstanceInRetry)))
      .never()

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute(flowInstance, TaskId(taskInstanceInRetry.taskId))

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

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus, _: Option[Int], _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstanceWithRetry.id, FlowTaskInstanceStatus.Retry, Some(0), Some(flowTaskInstance.retryDelay + testEpoch), *)
      .returns(Future.successful(Some(flowTaskInstanceWithRetry)))

    flowInstanceExecutorActor ! WorkFailed(new RuntimeException("error"), Some(flowTaskInstanceWithRetry))

    flowExecutorProbe.expectMsg(RetryScheduled(flowTaskInstanceWithRetry))
  }

  it should "execute task in retry on explicit execute" in new InstanceExecutorSetup {
    val flowInstanceExecutorProbe = TestProbe()
    val flowTaskExecutorProbe = TestProbe()

    val taskInstanceInRetry: FlowTaskInstanceDetails = flowTaskInstance.copy(status = FlowTaskInstanceStatus.Retry)

    (flowInstanceRepository.findById(_: String)(_: RepositoryContext))
      .expects(*, *)
      .returning(Future.successful(Some(flowInstance)))

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

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus, _: Option[Int], _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Running, *, *, *)
      .returns(Future.successful(Some(taskInstanceInRetry)))
      .noMoreThanTwice()

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus, _: Option[Int], _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Done, *, *, *)
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
    flowInstanceExecutorActor ! Execute(flowInstance, PendingTasks)

    flowExecutorProbe.expectMsg(WorkPending(flowInstance.id))

    // explicit execute, should execute retry
    flowInstanceExecutorActor ! Execute(flowInstance, TaskId(flowDefinition.tasks.head.id))

    flowExecutorProbe.expectMsg(WorkPending(flowInstance.id))
    flowExecutorProbe.expectMsg(TaskCompleted(taskInstanceInRetry))
  }

  it should "notify about failure when retries are exhausted" in new InstanceExecutorSetup {
    val flowInstanceExecutorProbe = TestProbe()
    val flowTaskExecutorProbe = TestProbe()

    val taskInstanceWithoutRetries: FlowTaskInstanceDetails = flowTaskInstance.copy(retries = 0)

    (flowTaskInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, testEpoch, *)
      .returns(Future.successful(Some(taskInstanceWithoutRetries)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus, _: Option[Int], _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Failed, *, *, *)
      .returns(Future.successful(Some(taskInstanceWithoutRetries)))

    flowInstanceExecutorActor ! WorkFailed(new RuntimeException("error"), Some(taskInstanceWithoutRetries))

    flowExecutorProbe.expectMsg(ExecutionFailed(flowInstance.id, flowDefinition.id))
  }

  it should "throttle task execution" in new InstanceExecutorSetup(
    SysiphosDefinition("ls-definition-id", Seq.tabulate(100)(index => CommandLineTask(s"ls-task-id-$index", None, "ls")), taskParallelism = Some(50))) {

    (flowInstanceRepository.findById(_: String)(_: RepositoryContext))
      .expects(*, *)
      .returning(Future.successful(Some(flowInstance)))
      .atLeastOnce()

    (flowTaskInstanceRepository.find(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id)), *)
      .returning(Future.successful(Seq.empty))
      .atLeastOnce()

    (flowTaskInstanceRepository.findOne(_: FlowTaskInstanceQuery)(_: RepositoryContext))
      .expects(*, *)
      .returning(Future.successful(None))
      .atLeastOnce()

    (flowTaskInstanceRepository.createFlowTaskInstance(_: String, _: String, _: String, _: String, _: Int, _: Long, _: Option[Long], _: Option[FlowTaskInstanceStatus.FlowTaskInstanceStatus])(_: RepositoryContext))
      .expects(flowInstance.id, *, *, *, *, *, *, *, *)
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

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus, _: Option[Int], _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Running, *, *, *)
      .returning(Future.successful(Some(flowTaskInstance)))
      .atLeastOnce()

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus, _: Option[Int], _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Done, *, *, *)
      .returning(Future.successful(Some(flowTaskInstance)))
      .atLeastOnce()

    flowInstanceExecutorActor ! FlowInstanceExecution.Execute(flowInstance, PendingTasks)

    val allInTenSeconds: Int = flowExecutorProbe.receiveWhile(10.seconds) {
      case TaskCompleted(_) => 1
      case _ => 0
    }.sum

    allInTenSeconds should be >= 2
    allInTenSeconds should be <= 10
  }
}
