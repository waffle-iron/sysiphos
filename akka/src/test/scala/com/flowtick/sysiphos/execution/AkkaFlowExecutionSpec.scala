package com.flowtick.sysiphos.execution

import java.util.concurrent.TimeUnit

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowInstance, FlowInstanceRepository }
import com.flowtick.sysiphos.scheduler._
import monix.execution.ExecutionModel.SynchronousExecution
import monix.execution.Scheduler
import monix.execution.schedulers.TestScheduler
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ FlatSpec, Matchers }

import scala.concurrent.Future
import scala.concurrent.duration.Duration

class AkkaFlowExecutionSpec extends FlatSpec with AkkaFlowExecution with Matchers with MockFactory {
  val scheduler: TestScheduler = TestScheduler().withExecutionModel(SynchronousExecution)

  override val flowInstanceRepository: FlowInstanceRepository[FlowInstance] = mock[FlowInstanceRepository[FlowInstance]]
  override val flowScheduleRepository: FlowScheduleRepository[FlowSchedule] = mock[FlowScheduleRepository[FlowSchedule]]
  override val flowScheduler: FlowScheduler = mock[FlowScheduler]
  override val flowScheduleStateStore: FlowScheduleStateStore = mock[FlowScheduleStateStore]
  override implicit val taskScheduler: Scheduler = scheduler

  override implicit val repositoryContext: RepositoryContext = new RepositoryContext {
    override def currentUser: String = "test-user"
  }

  final case class TestSchedule(
    id: String,
    expression: String,
    flowDefinitionId: String,
    flowTaskId: Option[String],
    nextDueDate: Option[Long],
    enabled: Option[Boolean]) extends CronSchedule

  "Akka flow executor" should "create child actors for due schedules" in new RepositoryContext {
    val testSchedule = TestSchedule(
      id = "test-schedule",
      expression = "0 1 * * *", // daily at 1:00 am
      flowDefinitionId = "flow-id",
      flowTaskId = None,
      nextDueDate = None,
      enabled = Some(true))
    val futureSchedules = Future.successful(Seq(testSchedule))

    (flowScheduleRepository.getFlowSchedules()(_: RepositoryContext)).expects(*).returning(futureSchedules)
    (flowScheduler.nextOccurrence _).expects(testSchedule, 0).returning(Some(1))
    (flowScheduleStateStore.setDueDate(_: String, _: Long)(_: RepositoryContext)).expects(testSchedule.id, 1, *)

    tick(now = 0).runAsync
    scheduler.tick(Duration(5, TimeUnit.MINUTES))

    override def currentUser: String = "test-user"
  }
}
