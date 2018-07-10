package com.flowtick.sysiphos.ui.vendor

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@JSGlobal("ace")
@js.native
object AceEditorSupport extends js.Any {
  @js.native
  trait Editor extends js.Any {
    def setValue(value: String, pos: Int = 0): js.native
    def resize(): Unit = js.native
    def setTheme(theme: String): js.native
    def session: Session = js.native
  }

  @js.native
  trait Session extends js.Any {
    def setMode(mode: String)
  }

  def edit(element: String): Editor = js.native
}