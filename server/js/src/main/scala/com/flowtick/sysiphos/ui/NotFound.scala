package com.flowtick.sysiphos.ui

import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Div

class NotFound extends HtmlComponent with Layout {
  @dom
  override val element: Binding[Div] =
    <div>
      <p>Could not find what you are looking for... <i class="fas fa-poo"></i>💩💩💩💩</p>
    </div>
}
