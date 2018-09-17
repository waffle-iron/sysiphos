package com.flowtick.sysiphos.util

import scala.util.matching.Regex

object Linkify {

  implicit class RegexContext(sc: StringContext) {
    def r = new Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
  }

  def linkify(text: String): String = {
    text.split(" ").map {
      case link @ r"(https?|ftp|file)$protocol://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*" =>
        s"""<a target="_blank" href="$link">$link</a>"""

      case other: String => other
    }.mkString(" ")
  }
}
