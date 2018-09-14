package com.flowtick.sysiphos.core

import java.time.{ LocalDateTime, ZoneId }

trait RepositoryContext {
  def currentUser: String
  def epochSeconds: Long = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond
}
