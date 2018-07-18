package com.flowtick.sysiphos.ui

import com.thoughtworks.binding.Binding.Vars
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Div
import org.scalajs.dom.window
import pages.DomView
import pages.Page.{ Component, page }

import scala.concurrent.ExecutionContext

object SysiphosUI extends App {
  case class DomComponent(element: Binding[Div]) extends Component[Binding[Div]]

  val currentView = Vars.empty[Binding[Div]]

  @dom
  def appView: Binding[Div] = {
    <div id="app-view">
      { for (view <- currentView) yield view.bind }
    </div>
  }

  val domView = new DomView[Binding[Div]](element => {
    currentView.value.clear()
    currentView.value += element
  })

  val api = new SysiphosApiClient()(ExecutionContext.global)
  val flowsComponent = new FlowsComponent(api)

  page[Binding[Div]]("/flows", _ => flowsComponent)
    .page("/flow/:id", ctx => ctx.pathParams.get("id").map(new FlowComponent(_, api)).getOrElse(new WelcomeComponent))
    .otherwise(_ => new WelcomeComponent)
    .view(domView)

  dom.render(window.document.getElementById("sysiphos-app"), appView)
}
