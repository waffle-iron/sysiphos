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

  "Akka flow executor" should "create child actors for due schedules" in new DefaultRepositoryContext("test-user") {
    val testInstance = FlowInstanceDetails(
      id = "test-instance",
      flowDefinitionId = "flow-id",
      creationTime = 0,
      startTime = None,
      endTime = None,
      status = FlowInstanceStatus.Scheduled,
      context = Seq.empty)

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
    (flowScheduleStateStore.setDueDate(_: String, _: Long)(_: RepositoryContext)).expects(testSchedule.id, 1, *).returning(Future.successful(()))

    dueScheduledFlowInstances(now = 0).futureValue
  }

  it should "respect the parallelism option" in {
    val flowDefinition: SysiphosDefinition = SysiphosDefinition(
      "ls-definition-id",
      Seq(CommandLineTask("ls-task-id", None, "ls")),
      parallelism = Some(1))

    val instances = Seq(
      FlowInstanceDetails(
        status = FlowInstanceStatus.Scheduled,
        id = "1",
        flowDefinitionId = flowDefinition.id,
        creationTime = 2L,
        context = Seq.empty,
        startTime = None,
        endTime = None),
      FlowInstanceDetails(
        status = FlowInstanceStatus.Scheduled,
        id = "2",
        flowDefinitionId = flowDefinition.id,
        creationTime = 2L,
        context = Seq.empty,
        startTime = None,
        endTime = None))

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

  override def executeInstance(instance: FlowInstance, selectedTaskId: Option[String]): Future[FlowInstance] =
    Future.successful(instance)

  override def executeRunning(
    running: FlowInstanceDetails,
    definition: FlowDefinition,
    selectedTaskId: Option[String]): Future[Any] = Future.successful()
}
