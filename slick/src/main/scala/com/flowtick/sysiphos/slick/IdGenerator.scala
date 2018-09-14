package com.flowtick.sysiphos.slick

import java.util.UUID

trait IdGenerator {
  def nextId: String
}

object DefaultIdGenerator extends IdGenerator {
  override def nextId: String = UUID.randomUUID().toString
}
