package com.flowtick.sysiphos.ui.util

import com.flowtick.sysiphos.ui.util.MomentJS.MomentApi

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.util.{ Failure, Success, Try }

@JSGlobal("moment")
@js.native
object MomentJS extends js.Any {

  @js.native
  trait MomentApi extends js.Any {
    def calendar: js.Any = js.native
    def format(format: String): String = js.native

    def utc(offset: js.UndefOr[Long] = js.undefined): MomentApi = js.native
    def unix(): Double = js.native

    def add(amount: Int, unit: String): MomentApi = js.native
    def subtract(amount: Int, unit: String): MomentApi = js.native

    def locale(): String = js.native
    def toISOString(): String = js.native
  }

  def apply(constructorValue: js.UndefOr[Any]): MomentApi = js.native

  def unix(epoch: Long): MomentApi = js.native
}

trait DateSupport {
  def formatDate(epochSeconds: Long): String = formatDate(epochSeconds, "YYYY-MM-DD HH:mm:ss")

  def formatDate(epochSeconds: Long, format: String): String = MomentJS
    .unix(epochSeconds)
    .format(format)

  def parseDate(date: String): Option[Long] = Try(MomentJS(date).unix().toLong) match {
    case Success(epoch) => Some(epoch).filter(_ != 0)
    case Failure(error) =>
      println(s"unable to parse date $date : $error")
      None
  }

  def now(): MomentApi = MomentJS()
}
