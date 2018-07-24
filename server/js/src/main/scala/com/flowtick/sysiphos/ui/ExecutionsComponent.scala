package com.flowtick.sysiphos.ui

import com.flowtick.sysiphos.flow.{ FlowDefinitionSummary, InstanceCount }
import com.flowtick.sysiphos.ui.vendor.ToastrSupport._
import com.thoughtworks.binding.Binding.{ Constants, Vars }
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.{ Button, Div, Table, TableRow }

import scala.concurrent.ExecutionContext.Implicits.global

class ExecutionsComponent(sysiphosApi: SysiphosApi) extends HtmlComponent with Layout {
  val flows: Vars[FlowDefinitionSummary] = Vars.empty[FlowDefinitionSummary]

  def loadDefinitions(): Unit = sysiphosApi.getFlowDefinitions.notifyError.foreach { response =>
    flows.value.clear()
    flows.value.append(response.data.definitions: _*)
  }

  override def init(): Unit = loadDefinitions()

  @dom
  def instanceCountButton(count: InstanceCount): Binding[Button] =
    <button type="button" class={
      count.status match {
        case "new" => "btn btn-info"
        case _ => "btn btn-default"
      }
    }><strong>{ count.status }</strong>&nbsp;<span class="badge"> { count.count.toString } </span></button>

  @dom
  def flowRow(flow: FlowDefinitionSummary): Binding[TableRow] =
    <tr>
      <td><a href={ "#/flow/show/" + flow.id }> { flow.id }</a></td>
      <td>
        <div class="btn-group" data:role="group" data:aria-label="count-buttons">
          { Constants(flow.counts: _*).map(instanceCountButton(_).bind) }
        </div>
      </td>
      <td>
        <button class="btn btn-success"><i class="glyphicon glyphicon-play"></i></button>
      </td>
    </tr>

  @dom
  def flowTable: Binding[Table] = {
    <table class="table table-striped">
      <thead>
        <th>ID</th>
        <th>Recent Executions</th>
        <th>Actions</th>
      </thead>
      <tbody>
        {
          for (flow <- flows) yield flowRow(flow).bind
        }
      </tbody>
    </table>
  }

  @dom
  def flowsSection: Binding[Div] = {
    <div>
      <h3>Flows <a class="btn btn-default" href="#/flow/new"><i class="fas fa-plus"></i> Add</a> </h3>
      {
        flowTable.bind
      }
    </div>
  }

  @dom
  override def element: Binding[Div] =
    <div>
      { layout(flowsSection.bind).bind }
    </div>

}
