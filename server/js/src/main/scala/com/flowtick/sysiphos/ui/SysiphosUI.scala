package com.flowtick.sysiphos.ui

import com.flowtick.sysiphos.ui.execution._
import com.flowtick.sysiphos.ui.flow._
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

  def flowsComponent = new FlowsComponent(new FlowsCircuit(api))
  def schedulesComponent(flowId: Option[String]) = new SchedulesComponent(flowId, new SchedulesCircuit(api))
  def instancesComponent(flowId: Option[String], statusCsv: Option[String], startDate: Option[String], endDate: Option[String]) = new FlowInstancesComponent(flowId, statusCsv, startDate, endDate, new FlowInstancesCircuit(api))
  def instanceComponent(instanceId: String) = new ShowInstanceComponent(instanceId, new ShowInstanceCircuit(api))
  def flowComponent(id: String) = new ShowFlowComponent(id)(new FlowCircuit(api), schedulesComponent(Some(id)))
  def runComponent(id: String) = new RunFlowComponent(id)(new FlowCircuit(api))
  def createFlowComponent = new CreateFlowComponent(new FlowCircuit(api))
  def logComponent(logId: String) = new LogComponent(logId, new LogCircuit(api))
  def notFound = new NotFound

  val routes: Routing[Binding[Div]] =
    page[Binding[Div]]("/flows", _ => flowsComponent)
      .page("/flow/new", _ => createFlowComponent)
      .page("/flow/run/:id", ctx => ctx.pathParams.get("id").map(id => runComponent(id)).getOrElse(notFound))
      .page("/flow/show/:id", ctx => ctx.pathParams.get("id").map(id => flowComponent(id)).getOrElse(notFound))
      .page("/schedules", _ => schedulesComponent(None))
      .page("/schedules/show/:flowId", ctx => ctx.pathParams.get("flowId").map(id => schedulesComponent(Some(id))).getOrElse(notFound))
      .page("/instances", ctx => instancesComponent(ctx.queryParams.get("flowId"), ctx.queryParams.get("status"), ctx.queryParams.get("startDate"), ctx.queryParams.get("endDate")))
      .page("/instances/show/:instanceId", ctx => ctx.pathParams.get("instanceId").map(id => instanceComponent(id)).getOrElse(notFound))
      .page("/not-found", _ => notFound)
      .page("/log/:logId", ctx => ctx.pathParams.get("logId").map(logComponent).getOrElse(notFound))
      .otherwise(_ => flowsComponent)

  com.thoughtworks.binding.dom.render(window.document.getElementById("sysiphos-app"), appView)

  routes.view(domView)
}
