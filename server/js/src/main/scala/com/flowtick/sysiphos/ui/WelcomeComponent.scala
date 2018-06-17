package com.flowtick.sysiphos.ui
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Div

class WelcomeComponent extends HtmlComponent with Layout {
  @dom
  def welcomeMessage: Binding[Div] = {
    <div>
      <p>Welcome to Sysihphos!</p>
    </div>
  }

  @dom
  override val element: Binding[Div] =
    <div>
      { layout(welcomeMessage.bind).bind }
    </div>
}
