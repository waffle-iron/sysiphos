package com.flowtick.sysiphos.core

import java.time.ZoneId

import org.scalatest.{ FlatSpec, Matchers }

class ClockSpec extends FlatSpec with Matchers {
  "Clock" should "handle epoch conversion with respect to timezone" in {
    for (zone <- Seq("UTC", "UTC+1")) {
      val clock = new Clock {
        override def timeZone: ZoneId = ZoneId.of(zone)
      }

      val currentTime = clock.currentTime

      currentTime.toEpochSecond should be(clock.fromEpochSeconds(currentTime.toEpochSecond).toEpochSecond)
    }
  }
}
