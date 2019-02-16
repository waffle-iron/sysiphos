package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.core.{ DefaultRepositoryContext, RepositoryContext }
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.scheduler._
import com.flowtick.sysiphos.task.CommandLineTask
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.{ FlatSpec, Matchers }

import scala.concurrent.{ ExecutionContext, Future }

class FlowExecutionSpec extends FlatSpec with FlowExecution with Matchers with MockFactory
  with ScalaFutures with IntegrationPatience {

  override val flowInstanceRepository: FlowInstanceRepository = mock[FlowInstanceRepository]
  override val flowDefinitionRepository: FlowDefinitionRepository = mock[FlowDefinitionRepository]
  override val flowScheduleRepository: FlowScheduleRepository = mock[FlowScheduleRepository]
  override val flowScheduler: FlowScheduler = mock[FlowScheduler]
  override val flowTaskInstanceRepository: FlowTaskInstanceRepository = mock[FlowTaskInstanceRepository]
  override val flowScheduleStateStore: FlowScheduleStateStore = mock[FlowScheduleStateStore]

  override implicit val repositoryContext: RepositoryContext = new DefaultRepositoryContext("test-user")

  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  val testSchedule = FlowScheduleDetails(
    "test-schedule",
    "test",
    0,
    0,
    None,
    None,
    "flow-definition",
    None,
    nextDueDate = Some(0),
    enabled = Some(true),
    backFill = Some(false))

  val newInstance = FlowInstanceContext(FlowInstanceDetails(
    status = FlowInstanceStatus.Scheduled,
    id = "1",
    flowDefinitionId = testSchedule.flowDefinitionId,
    creationTime = 2L,
    startTime = None,
    endTime = None), Seq.empty)

  "Akka flow executor" should "create child actors for due schedules" in new DefaultRepositoryContext("test-user") {
    val testInstance = FlowInstanceContext(FlowInstanceDetails(
      id = "test-instance",
      flowDefinitionId = "flow-id",
      creationTime = 0,
      startTime = None,
      endTime = None,
      status = FlowInstanceStatus.Scheduled), Seq.empty)

    val testSchedule = FlowScheduleDetails(
      id = "test-schedule",
      expression = Some("0 1 * * *"), // daily at 1:00 am
      flowDefinitionId = "flow-id",
      flowTaskId = None,
      nextDueDate = None,
      enabled = Some(true),
      created = 0,
      updated = None,
      creator = "test",
      version = 0,
      backFill = None)
    val futureSchedules = Future.successful(Seq(testSchedule))

    (flowScheduleRepository.getFlowSchedules(_: Option[Boolean], _: Option[String])(_: RepositoryContext)).expects(*, *, *).returning(futureSchedules)
    (flowInstanceRepository.createFlowInstance(_: String, _: Seq[FlowInstanceContextValue], _: FlowInstanceStatus.FlowInstanceStatus)(_: RepositoryContext)).expects("flow-id", Seq.empty[FlowInstanceContextValue], FlowInstanceStatus.Scheduled, *).returning(Future.successful(testInstance))
    (flowScheduler.nextOccurrence _).expects(testSchedule, 0).returning(Some(1))
    (flowScheduler.missedOccurrences _).expects(*, *).returning(Seq.empty)
    (flowScheduleStateStore.setDueDate(_: String, _: Long)(_: RepositoryContext)).expects(testSchedule.id, 1, *).returning(Future.successful(()))

    dueScheduledFlowInstances(now = 0).futureValue
  }

  it should "respect the parallelism option" in {
    val flowDefinition: SysiphosDefinition = SysiphosDefinition(
      "ls-definition-id",
      Seq(CommandLineTask("ls-task-id", None, "ls")),
      parallelism = Some(1))

    val instances = Seq(
      FlowInstanceContext(FlowInstanceDetails(
        status = FlowInstanceStatus.Scheduled,
        id = "1",
        flowDefinitionId = flowDefinition.id,
        creationTime = 2L,
        startTime = None,
        endTime = None), Seq.empty),
      FlowInstanceContext(FlowInstanceDetails(
        status = FlowInstanceStatus.Scheduled,
        id = "2",
        flowDefinitionId = flowDefinition.id,
        creationTime = 2L,
        startTime = None,
        endTime = None), Seq.empty))

    (flowInstanceRepository.counts _).expects(
      Option(Seq(flowDefinition.id)),
      Option(Seq(FlowInstanceStatus.Running)),
      None).returning(Future.successful(Seq.empty)).twice()

    executeInstances(flowDefinition, instances).futureValue should have size 1
    executeInstances(flowDefinition.copy(parallelism = Some(2)), instances).futureValue should have size 2

    (flowInstanceRepository.counts _).expects(
      Option(Seq(flowDefinition.id)),
      Option(Seq(FlowInstanceStatus.Running)),
      None).returning(Future.successful(Seq(InstanceCount(flowDefinition.id, FlowInstanceStatus.Running.toString, 1))))

    executeInstances(flowDefinition.copy(parallelism = Some(2)), instances).futureValue should have size 1
  }

  it should "return new instances when applying the schedule" in {
    val backFillDisabled = testSchedule.copy(backFill = Some(true))

    (flowScheduler.nextOccurrence _).expects(backFillDisabled, 1).returning(Some(2))
    (flowScheduler.missedOccurrences _).expects(backFillDisabled, 1).returning(Seq.empty)

    (flowScheduleStateStore.setDueDate(_: String, _: Long)(_: RepositoryContext))
      .expects(backFillDisabled.id, 2, *)
      .returning(Future.successful())

    (flowInstanceRepository.createFlowInstance(_: String, _: Seq[FlowInstanceContextValue], _: FlowInstanceStatus.FlowInstanceStatus)(_: RepositoryContext))
      .expects("flow-definition", Seq.empty[FlowInstanceContextValue], *, *)
      .returning(Future.successful(newInstance))

    applySchedule(backFillDisabled, 1).futureValue should be(Some(Seq(newInstance)))
  }

  it should "return missed occurrences only when back fill is enabled" in {
    (flowScheduler.nextOccurrence _).expects(testSchedule, 1).returning(Some(2))
    (flowScheduler.missedOccurrences _).expects(*, *).never()

    (flowScheduleStateStore.setDueDate(_: String, _: Long)(_: RepositoryContext))
      .expects(*, *, *)
      .returning(Future.successful())

    (flowInstanceRepository.createFlowInstance(_: String, _: Seq[FlowInstanceContextValue], _: FlowInstanceStatus.FlowInstanceStatus)(_: RepositoryContext))
      .expects("flow-definition", Seq.empty[FlowInstanceContextValue], *, *)
      .returning(Future.successful(newInstance))

    applySchedule(testSchedule, 1).futureValue should be(Some(Seq(newInstance)))
  }

  override def executeInstance(instance: FlowInstanceContext, selectedTaskId: Option[String]): Future[FlowInstance] =
    Future.successful(instance.instance)

  override def executeRunning(
    running: FlowInstanceDetails,
    selectedTaskId: Option[String]): Future[Any] = Future.successful()
}
