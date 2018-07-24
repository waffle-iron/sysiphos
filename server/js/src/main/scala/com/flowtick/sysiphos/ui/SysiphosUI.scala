package com.flowtick.sysiphos.ui

import com.thoughtworks.binding.Binding.Vars
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Div
import org.scalajs.dom.{ Event, HashChangeEvent, window }
import pages.{ DomView, Page }
import pages.Page.{ Component, Routing, View, page }

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
  val schedulesComponent = new SchedulesComponent(None, api)

  page[Binding[Div]]("/flows", _ => flowsComponent)
    .page("/flow/show/:id", ctx => new FlowComponent(ctx.pathParams.get("id"), api))
    .page("/flow/new", _ => new FlowComponent(None, api))
    .page("/schedules", _ => schedulesComponent)
    .page("/schedules/show/:flowId", ctx => new SchedulesComponent(ctx.pathParams.get("flowId"), api))
    .otherwise(_ => new WelcomeComponent)
    .view(domView)

  com.thoughtworks.binding.dom.render(window.document.getElementById("sysiphos-app"), appView)
}
