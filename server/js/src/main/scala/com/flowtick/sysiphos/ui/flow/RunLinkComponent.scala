package com.flowtick.sysiphos.ui.flow

import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Anchor

trait RunLinkComponent {
  @dom
  def runLink(flowDefinitionId: String): Binding[Anchor] =
    <a href={ s"#/flow/run/$flowDefinitionId" } class="btn btn-success"><i class="fas fa-play"></i></a>

}
