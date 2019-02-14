package com.flowtick.sysiphos.ui.vendor

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

/**
 * Types for https://kimmobrunfeldt.github.io/progressbar.js/
 */
@JSGlobal("ProgressBar")
@js.native
object ProgressBar extends js.Object {

  @js.native
  trait ProgressBar extends js.Object {
    def animate(targetValue: Double): Unit = js.native
  }

  @js.native
  class Line(container: String, options: js.Dictionary[Any] = js.Dictionary()) extends ProgressBar
}
