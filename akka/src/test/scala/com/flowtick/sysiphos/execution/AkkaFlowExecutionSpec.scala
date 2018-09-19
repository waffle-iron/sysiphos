package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowInstanceDetails, FlowInstanceRepository, FlowInstanceStatus, FlowTaskInstanceRepository }
import com.flowtick.sysiphos.scheduler._
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.{ FlatSpec, Matchers }

import scala.concurrent.{ ExecutionContext, Future }

class AkkaFlowExecutionSpec extends FlatSpec with FlowExecution with Matchers with MockFactory
  with ScalaFutures with IntegrationPatience {

  override val flowInstanceRepository: FlowInstanceRepository = mock[FlowInstanceRepository]
  override val flowScheduleRepository: FlowScheduleRepository = mock[FlowScheduleRepository]
  override val flowScheduler: FlowScheduler = mock[FlowScheduler]
  override val flowTaskInstanceRepository: FlowTaskInstanceRepository = mock[FlowTaskInstanceRepository]
  override val flowScheduleStateStore: FlowScheduleStateStore = mock[FlowScheduleStateStore]

  override implicit val repositoryContext: RepositoryContext = new RepositoryContext {
    override def currentUser: String = "test-user"
  }

  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  "Akka flow executor" should "create child actors for due schedules" in new RepositoryContext {
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
      enabled = Some(true), created = 0, updated = None, creator = "test", version = 0)
    val futureSchedules = Future.successful(Seq(testSchedule))

    (flowScheduleRepository.getFlowSchedules(_: Option[Boolean], _: Option[String])(_: RepositoryContext)).expects(*, *, *).returning(futureSchedules)
    (flowInstanceRepository.createFlowInstance(_: String, _: Map[String, String], _: FlowInstanceStatus.FlowInstanceStatus)(_: RepositoryContext)).expects("flow-id", Map.empty[String, String], FlowInstanceStatus.Scheduled, *).returning(Future.successful(testInstance))
    (flowScheduler.nextOccurrence _).expects(testSchedule, 0).returning(Some(1))
    (flowScheduleStateStore.setDueDate(_: String, _: Long)(_: RepositoryContext)).expects(testSchedule.id, 1, *).returning(Future.successful(()))

    dueScheduledFlowInstances(now = 0).futureValue

    override def currentUser: String = "test-user"
  }
}
