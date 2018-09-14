package com.flowtick.sysiphos.ui

import com.flowtick.sysiphos.ui.execution.{ FlowInstancesCircuit, FlowInstancesComponent, ShowInstanceCircuit, ShowInstanceComponent }
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
  def instancesComponent(flowId: Option[String], status: Option[String]) = new FlowInstancesComponent(flowId, status, new FlowInstancesCircuit(api))
  def instanceComponent(instanceId: String) = new ShowInstanceComponent(instanceId, new ShowInstanceCircuit(api))
  def flowComponent(id: String) = new ShowFlowComponent(id)(new FlowCircuit(api), schedulesComponent(Some(id)))
  def createFlowComponent = new CreateFlowComponent(new FlowCircuit(api))
  def notFound = new NotFound

  val routes: Routing[Binding[Div]] =
    page[Binding[Div]]("/flows", _ => flowsComponent)
      .page("/flow/new", _ => createFlowComponent)
      .page("/flow/show/:id", ctx => ctx.pathParams.get("id").map(id => flowComponent(id)).getOrElse(notFound))
      .page("/schedules", _ => schedulesComponent(None))
      .page("/schedules/show/:flowId", ctx => ctx.pathParams.get("flowId").map(id => schedulesComponent(Some(id))).getOrElse(notFound))
      .page("/instances", ctx => instancesComponent(None, ctx.queryParams.get("status")))
      .page("/instances/filter/:flowId", ctx => instancesComponent(ctx.pathParams.get("flowId"), ctx.queryParams.get("status")))
      .page("/instances/show/:instanceId", ctx => ctx.pathParams.get("instanceId").map(id => instanceComponent(id)).getOrElse(notFound))
      .page("/not-found", _ => notFound)
      .otherwise(_ => flowsComponent)

  com.thoughtworks.binding.dom.render(window.document.getElementById("sysiphos-app"), appView)

  routes.view(domView)
}
