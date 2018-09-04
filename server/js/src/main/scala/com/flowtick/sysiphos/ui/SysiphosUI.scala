package com.flowtick.sysiphos.ui

import com.flowtick.sysiphos.ui.flow.{ CreateFlowComponent, FlowCircuit, FlowsComponent, ShowFlowComponent }
import com.flowtick.sysiphos.ui.schedule.{ SchedulesCircuit, SchedulesComponent }
import com.thoughtworks.binding.Binding.Var
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Div
import org.scalajs.dom.window
import pages.DomView
import pages.Page.{ Routing, page }

import scala.concurrent.ExecutionContext

object SysiphosUI extends App with Layout {
  val currentView: Var[Option[HtmlComponent]] = Var(None)

  @dom
  def appView: Binding[Div] = {
    <div id="app-view">
      {
        currentView.bind match {
          case Some(view) =>
            val viewElement = view.element.bind
            view.init
            layout(viewElement).bind
          case None => <!-- no view -->
        }
      }
    </div>
  }

  val domView = new DomView[Binding[Div]]({
    case html: HtmlComponent => currentView.value = Some(html)
    case _ =>
  })

  val api = new SysiphosApiClient()(ExecutionContext.global)

  def flowsComponent = new FlowsComponent(api)
  def schedulesComponent(flowId: Option[String]) = new SchedulesComponent(flowId, new SchedulesCircuit(api))
  def flowComponent(id: String) = new ShowFlowComponent(id)(new FlowCircuit(api), schedulesComponent(Some(id)))
  def createFlowComponent = new CreateFlowComponent(new FlowCircuit(api))
  def welcome = new WelcomeComponent

  val routes: Routing[Binding[Div]] =
    page[Binding[Div]]("/flows", _ => flowsComponent)
      .page("/flow/new", _ => createFlowComponent)
      .page("/flow/show/:id", ctx => ctx.pathParams.get("id").map(id => flowComponent(id)).getOrElse(welcome))
      .page("/schedules", _ => schedulesComponent(None))
      .page("/schedules/show/:flowId", ctx => ctx.pathParams.get("flowId").map(id => schedulesComponent(Some(id))).getOrElse(welcome))
      .otherwise(_ => welcome)

  com.thoughtworks.binding.dom.render(window.document.getElementById("sysiphos-app"), appView)

  routes.view(domView)
}
