package com.flowtick.sysiphos.core

trait RepositoryContext {
  def currentUser: String
  def epochSeconds: Long
}
