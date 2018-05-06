package com.flowtick.sysiphos.slick

import slick.lifted.Rep

trait SysiphosTable[T] {
  def creator: Rep[String]
  def created: Rep[Long]
  def updated: Rep[Long]
}
