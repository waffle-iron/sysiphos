package com.flowtick.sysiphos

import com.thoughtworks.binding.Binding
import org.scalajs.dom.html.Div
import pages.Page.Component

package object ui {
  trait HtmlComponent extends Component[Binding[Div]]
}
