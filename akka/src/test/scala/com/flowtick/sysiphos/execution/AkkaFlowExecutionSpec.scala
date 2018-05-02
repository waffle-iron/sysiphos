package com.flowtick.sysiphos.execution

import java.util.concurrent.TimeUnit

import com.flowtick.sysiphos.flow.FlowInstanceRepository
import com.flowtick.sysiphos.scheduler.{ CronSchedule, FlowScheduleRepository, FlowScheduler }
import monix.execution.ExecutionModel.SynchronousExecution
import monix.execution.Scheduler
import monix.execution.schedulers.TestScheduler
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ FlatSpec, Matchers }

import scala.concurrent.Future
import scala.concurrent.duration.Duration

class AkkaFlowExecutionSpec extends FlatSpec with AkkaFlowExecution with Matchers with MockFactory {
  val scheduler: TestScheduler = TestScheduler().withExecutionModel(SynchronousExecution)

  override val flowInstanceRepository: FlowInstanceRepository = mock[FlowInstanceRepository]
  override val flowScheduleRepository: FlowScheduleRepository = mock[FlowScheduleRepository]
  override val flowScheduler: FlowScheduler = mock[FlowScheduler]
  override implicit val taskScheduler: Scheduler = scheduler

  "Akka flow executor" should "create child actors for due schedules" in {
    val testSchedule = CronSchedule(
      id = "test-schedule",
      expression = "0 1 * * *", // daily at 1:00 am
      flowDefinitionId = "flow-id",
      flowTaskId = None,
      nextDueDate = None,
      enabled = Some(true))
    val futureSchedules = Future.successful(Seq(testSchedule))

    (flowScheduleRepository.getFlowSchedules _).expects().returning(futureSchedules)
    (flowScheduler.nextOccurrence _).expects(testSchedule, 0).returning(Some(1))
    (flowScheduleRepository.setDueDate _).expects(testSchedule, 1)

    tick(now = 0).runAsync
    scheduler.tick(Duration(5, TimeUnit.MINUTES))
  }
}
