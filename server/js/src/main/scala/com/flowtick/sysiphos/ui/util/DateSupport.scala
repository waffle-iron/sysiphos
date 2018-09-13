package com.flowtick.sysiphos.ui.util

import scala.scalajs.js.Date

trait DateSupport {
  def formatDate(epochSeconds: Long): String = {
    val date = new Date(epochSeconds * 1000)
    s"${date.toDateString()}, ${date.toTimeString()}"
  }

  def nowMinusHours(hours: Int): Long = (new Date().getTime() / 1000).toLong - hours * 3600
}
