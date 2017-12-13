package com.flowtick.sysiphos.ui
import scala.xml.Elem

class FlowsComponent extends HtmlComponent with Layout {
  override val element: Elem = layout(<div>Flows</div>)
}
