package com.flowtick.sysiphos.ui
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Div

class FlowComponent(id: String) extends HtmlComponent with Layout {
  @dom
  def flowSection: Binding[Div] = {
    <div>
      <h3>Flow { id }</h3>
    </div>
  }

  @dom
  def noId: Binding[Div] = <div>No ID definied</div>

  @dom
  override val element: Binding[Div] = {
    <div>
      { layout(flowSection.bind).bind }
    </div>
  }
}
