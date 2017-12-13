package com.flowtick.sysiphos.ui
import scala.xml.Elem

class WelcomeComponent extends HtmlComponent with Layout {
  override val element: Elem = layout(<p>Welcome to Sysihphos!</p>)
}
