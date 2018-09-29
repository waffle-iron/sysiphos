package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.scheduler.FlowScheduleDetails
import org.scalatest.{ FlatSpec, Matchers }

class CronSchedulerSpec extends FlatSpec with Matchers {
  "Cron Scheduler" should "give next occurrence for cron schedule" in {
    val testSchedule = FlowScheduleDetails(
      id = "test-schedule",
      creator = "test",
      created = 0,
      version = 0,
      updated = None,
      expression = Some("0 * * ? * *"), // every minute on second 0
      flowDefinitionId = "test-flow-id",
      flowTaskId = None,
      nextDueDate = None,
      enabled = None,
      backFill = None)

    val next = CronScheduler.nextOccurrence(testSchedule, 0)
    next should be(Some(60))
  }

  it should "take the next occurrence relative to old schedule date if defined and back fill enabled" in {
    val testSchedule = FlowScheduleDetails(
      id = "test-schedule",
      creator = "test",
      created = 0,
      version = 0,
      updated = None,
      expression = Some("0 * * ? * *"), // every minute on second 0
      flowDefinitionId = "test-flow-id",
      flowTaskId = None,
      nextDueDate = Some(60),
      enabled = None,
      backFill = None)

    val now = 180
    val nextBackFill = CronScheduler.nextOccurrence(testSchedule.copy(backFill = Some(true)), now)
    nextBackFill should be(Some(120))

    val nextNoBackFill = CronScheduler.nextOccurrence(testSchedule.copy(backFill = Some(false)), now)
    nextNoBackFill should be(Some(240))
  }

}
