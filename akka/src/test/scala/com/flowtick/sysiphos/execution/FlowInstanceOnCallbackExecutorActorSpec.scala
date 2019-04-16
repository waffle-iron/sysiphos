package com.flowtick.sysiphos.execution

import java.time.{ LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime }

import akka.actor.{ ActorSystem, PoisonPill, Props }
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit, TestProbe }
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowInstanceExecution._
import com.flowtick.sysiphos.flow.FlowDefinition.{ SysiphosDefinition, SysiphosTask }
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.logging.ConsoleLogger
import com.flowtick.sysiphos.scheduler.{ FlowScheduleRepository, FlowScheduleStateStore }
import com.flowtick.sysiphos.task.CommandLineTask
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }

import scala.concurrent.{ ExecutionContext, Future }

class FlowInstanceOnCallbackExecutorActorSpec extends TestKit(ActorSystem("instance-executor-spec"))
  with ImplicitSender
  with MockFactory
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll {

  class InstanceExecutorSetup(val flowDefinition: FlowDefinition = SysiphosDefinition(
    "ls-definition-id",
    Seq(CommandLineTask("ls-task-id", None, "ls")),
    onFailure = Some(SysiphosTask("on-failure-task-id", `type` = "console", None, None)))) {
    val flowDefinitionRepository: FlowDefinitionRepository = mock[FlowDefinitionRepository]
    val flowInstanceRepository: FlowInstanceRepository = mock[FlowInstanceRepository]
    val flowTaskInstanceRepository: FlowTaskInstanceRepository = mock[FlowTaskInstanceRepository]
    val flowScheduleRepository: FlowScheduleRepository = mock[FlowScheduleRepository]
    val flowScheduleStateStore: FlowScheduleStateStore = mock[FlowScheduleStateStore]

    val executorActorProps = Props[FlowExecutorActor](new FlowExecutorActor(
      flowScheduleRepository,
      flowInstanceRepository,
      flowDefinitionRepository,
      flowTaskInstanceRepository,
      flowScheduleStateStore,
      CronScheduler)(ExecutionContext.global))

    lazy val flowInstance = FlowInstanceDetails(
      status = FlowInstanceStatus.Scheduled,
      id = "???",
      flowDefinitionId = flowDefinition.id,
      creationTime = 1L,
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

    lazy val flowOnFailureTaskInstance = FlowTaskInstanceDetails(
      id = "on-failure-task-instance-id",
      flowInstanceId = flowInstance.id,
      flowDefinitionId = flowDefinition.id,
      taskId = flowDefinition.onFailure.get.id,
      creationTime = 1l,
      startTime = None,
      endTime = None,
      retries = 0,
      status = FlowTaskInstanceStatus.New,
      retryDelay = 10,
      nextDueDate = None,
      logId = "log-id-1")

    lazy val logger = new ConsoleLogger

    val testEpoch = 42

    implicit val repositoryContext: RepositoryContext = new RepositoryContext {
      override def currentUser: String = "test-user"

      override def epochSeconds: Long = testEpoch
    }

    lazy val flowExecutorProbe = TestProbe()
    lazy val flowInstanceExecutorActor = TestActorRef(flowInstanceActorProps)
    lazy val flowExecutorActor = TestActorRef(executorActorProps)

    def flowInstanceActorProps = Props(
      new FlowInstanceExecutorActor(flowInstance.id, flowInstance.flowDefinitionId)(
        DefaultClusterContext(flowDefinitionRepository = flowDefinitionRepository, flowInstanceRepository = flowInstanceRepository, flowTaskInstanceRepository = flowTaskInstanceRepository, flowScheduleRepository = null, flowScheduleStateStore = null),
        flowExecutorProbe.ref,
        logger)(repositoryContext, ExecutionContext.Implicits.global) {
        override def currentTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC), ZoneId.systemDefault())
      })

  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "FlowInstanceExecutorActor" should "pass to flowExecutor failed task if exist" in new InstanceExecutorSetup() {

    val taskInstanceWithoutRetries: FlowTaskInstanceDetails = flowTaskInstance.copy(retries = 0)

    (flowDefinitionRepository.findById(_: String)(_: RepositoryContext))
      .expects("ls-definition-id", *)
      .returning(Future.successful(Option(FlowDefinitionDetails(
        flowDefinition.id,
        None,
        Some(FlowDefinition.toJson(flowDefinition)),
        Some(0))))).anyNumberOfTimes()

    (flowInstanceRepository.getContextValues(_: String)(_: RepositoryContext))
      .expects(*, *)
      .returning(Future.successful(Seq.empty))
      .anyNumberOfTimes()

    (flowTaskInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext))
      .expects(flowTaskInstance.id, testEpoch, *)
      .returns(Future.successful(Some(taskInstanceWithoutRetries)))

    (flowTaskInstanceRepository.setStatus(_: String, _: FlowTaskInstanceStatus.FlowTaskInstanceStatus, _: Option[Int], _: Option[Long])(_: RepositoryContext))
      .expects(flowTaskInstance.id, FlowTaskInstanceStatus.Failed, *, *, *)
      .returns(Future.successful(Some(taskInstanceWithoutRetries)))

    flowInstanceExecutorActor ! WorkFailed(new RuntimeException("error"), Some(taskInstanceWithoutRetries))

    flowExecutorProbe.fishForMessage() {
      case ExecutionFailed(_, flowInstanceId, flowDefinitionId, Some(onFailureTaskId)) =>
        flowInstance.id == flowInstanceId && flowDefinition.id == flowDefinitionId && onFailureTaskId == "on-failure-task-id"
    }

  }

  "FlowExecutorActor" should "pass the running id back if the on failure callback is set" in new InstanceExecutorSetup() {
    val error = Some(new RuntimeException("error"))
    (flowInstanceRepository.update(_: FlowInstanceQuery, _: FlowInstanceStatus.FlowInstanceStatus, _: Option[Throwable])(_: RepositoryContext))
      .expects(
        FlowInstanceQuery(instanceIds = Some(Seq(flowInstance.id))), FlowInstanceStatus.Failed, error, *)
      .returns(Future.successful())

    (flowInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext)).expects(*, *, *)

    flowExecutorActor ! ExecutionFailed(error, flowInstance.id, flowDefinition.id, Some("on-failure-task-id"))

    expectMsg(FlowInstanceExecution.Run(TaskId("on-failure-task-id")))
  }

  it should "send PoisonPill in case the on failure callback is NOT set" in new InstanceExecutorSetup() {
    val probe = TestProbe()
    probe watch self

    val error = Some(new RuntimeException("error"))
    (flowInstanceRepository.update(_: FlowInstanceQuery, _: FlowInstanceStatus.FlowInstanceStatus, _: Option[Throwable])(_: RepositoryContext))
      .expects(
        FlowInstanceQuery(instanceIds = Some(Seq(flowInstance.id))), FlowInstanceStatus.Failed, error, *)
      .returns(Future.successful())

    (flowInstanceRepository.setEndTime(_: String, _: Long)(_: RepositoryContext)).expects(*, *, *)

    flowExecutorActor ! ExecutionFailed(error, flowInstance.id, flowDefinition.id, None)

    probe.expectTerminated(self)

  }

}
