package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.scheduler.CronSchedule
import org.scalatest.{ FlatSpec, Matchers }

class CronSchedulerSpec extends FlatSpec with Matchers {
  "Cron Scheduler" should "give next occurrence for cron schedule" in {
    val next = CronScheduler.nextOccurrence(CronSchedule("test-schedule", "10-35 2,4,6 * ? * *", "test-flow-id"), 0)
    next should be(Some(130))
  }
}
