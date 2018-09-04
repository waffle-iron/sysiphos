package com.flowtick.sysiphos

import com.thoughtworks.binding.Binding
import org.scalajs.dom.html.Div

package object ui {
  import pages.Page.Component

  trait HtmlComponent extends Component[Binding[Div]] {
    def init: Unit = ()
  }
}
