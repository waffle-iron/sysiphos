package com.flowtick.sysiphos.ui.flow

import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Anchor

trait RunFlowComponent {

  @dom
  def runLink(flowId: String): Binding[Anchor] =
    <a href={ s"/graphiql?query=mutation {%0A%09createInstance(flowDefinitionId%3A%20%22${flowId}%22%2C%20context%3A []) {%0A%20%20%20 id%2C context {value}%0A%20 }%0A}" } class="btn btn-success"><i class="glyphicon glyphicon-play"></i></a>

}
