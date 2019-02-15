package com.flowtick.sysiphos.core

class DefaultRepositoryContext(val currentUser: String) extends RepositoryContext with Clock {
  override def epochSeconds: Long = currentTime.toEpochSecond
}