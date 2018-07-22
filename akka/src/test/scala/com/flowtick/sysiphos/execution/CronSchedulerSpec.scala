package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.scheduler.FlowSchedule
import org.scalatest.{FlatSpec, Matchers}

class CronSchedulerSpec extends FlatSpec with Matchers {
  "Cron Scheduler" should "give next occurrence for cron schedule" in {
    val testSchedule = new FlowSchedule {
      override def expression: Option[String] = Some("10-35 2,4,6 * ? * *")

      override def id: String = "test-schedule"

      override def flowDefinitionId: String = "test-flow-id"

      override def flowTaskId: Option[String] = None

      override def nextDueDate: Option[Long] = None

      override def enabled: Option[Boolean] = None
    }

    val next = CronScheduler.nextOccurrence(testSchedule, 0)
    next should be(Some(130))
  }
}
