package com.flowtick.sysiphos.ui.vendor

trait SourceEditor {
  def sourceEditor(elementId: String, mode: String): AceEditorSupport.Editor = {
    val editor = AceEditorSupport.edit(elementId)
    editor.setTheme("ace/theme/textmate")
    editor.session.setMode(s"ace/mode/$mode")
    editor
  }
}
