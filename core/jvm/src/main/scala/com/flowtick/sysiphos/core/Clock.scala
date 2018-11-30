package com.flowtick.sysiphos.core

import java.time.temporal.Temporal
import java.time.{ LocalDateTime, ZoneId, ZoneOffset, ZonedDateTime }

trait Clock {
  def timeZone: ZoneId = ZoneId.systemDefault()
  def zoneOffset(temporal: Temporal): ZoneOffset = ZoneOffset.from(temporal)

  def fromEpochSeconds(epoch: Long): LocalDateTime = LocalDateTime.ofEpochSecond(epoch, 0, zoneOffset(now))
  def now: ZonedDateTime = LocalDateTime.now().atZone(timeZone)
  def epochSeconds: Long = now.toLocalDateTime.toEpochSecond(zoneOffset(now))
}
