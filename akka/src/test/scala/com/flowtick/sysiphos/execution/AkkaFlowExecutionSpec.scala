package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository, FlowScheduler }
import monix.execution.Scheduler
import monix.execution.schedulers.TestScheduler
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ FlatSpec, Matchers }

class AkkaFlowExecutionSpec extends FlatSpec with AkkaFlowExecution with Matchers with MockFactory {
  override val flowScheduleRepository: FlowScheduleRepository = mock[FlowScheduleRepository]
  override val flowScheduler: FlowScheduler = mock[FlowScheduler]
  override implicit val taskScheduler: Scheduler = TestScheduler()

  "Akka flow executor" should "create child actors for due schedules" in {
    tick(0)
  }

  override def createInstance(flowSchedule: FlowSchedule): Unit = ???
}
