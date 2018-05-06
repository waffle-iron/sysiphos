package com.flowtick.sysiphos.core

import java.time.{ LocalDateTime, ZoneOffset }

trait RepositoryContext {
  def currentUser: String
  def epochSeconds: Long = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
}
