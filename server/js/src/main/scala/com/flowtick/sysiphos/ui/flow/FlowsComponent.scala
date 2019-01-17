package com.flowtick.sysiphos.ui.flow

import com.flowtick.sysiphos.flow.{ FlowDefinitionSummary, FlowInstanceStatus, InstanceCount }
import com.flowtick.sysiphos.ui.execution.FlowInstanceStatusHelper
import com.flowtick.sysiphos.ui.{ HtmlComponent, Layout }
import com.thoughtworks.binding.Binding.{ Constants, Vars }
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.Event
import org.scalajs.dom.html._

class FlowsComponent(circuit: FlowsCircuit) extends HtmlComponent with RunLinkComponent with Layout {
  val flows: Vars[FlowDefinitionSummary] = Vars.empty[FlowDefinitionSummary]

  override def init: Unit = {
    circuit.subscribe(circuit.zoom(identity)) { model =>
      flows.value.clear()
      flows.value.appendAll(model.value.flowDefinitions)
    }

    circuit.dispatch(LoadFlows)
  }

  def deleteFlowDefinition(flowDefinitionId: String): Unit = {
    if (org.scalajs.dom.window.confirm(s"Do you really want to delete flow definition $flowDefinitionId?")) {
      circuit.dispatch(DeleteFlow(flowDefinitionId))
    }
  }

  @dom
  def instanceCountButton(count: InstanceCount): Binding[Anchor] =
    <a href={ s"#/instances?flowId=${count.flowDefinitionId}&status=${count.status}" } class={ FlowInstanceStatusHelper.instanceStatusButtonClass(FlowInstanceStatus.withName(count.status)) }><strong>{ count.status }</strong>&nbsp;<span class="badge"> { count.count.toString } </span></a>

  @dom
  def flowRow(flow: FlowDefinitionSummary): Binding[TableRow] =
    <tr>
      <td><a href={ "#/flow/show/" + flow.id }> { flow.id }</a></td>
      <td>
        <div class="btn-group" data:role="group" data:aria-label="count-buttons">
          { Constants(flow.counts: _*).map(instanceCountButton(_).bind) }
          <a class="btn btn-default" href={ s"#/instances?flowId=${flow.id}" }>
            <strong>all</strong>
            <span class="badge"><i class="fas fa-search"></i></span>
          </a>
        </div>
      </td>
      <td>
        <div class="btn-group">
          { runLink(flow.id).bind }
          <a class="btn btn-danger" onclick={ _: Event => deleteFlowDefinition(flow.id) }><i class="fas fa-trash"></i></a>
        </div>
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
  override def element: Binding[Div] =
    <div id="flows">
      <h3>Flows <a class="btn btn-default" href="#/flow/new"><i class="fas fa-plus"></i> Add</a> </h3>
      {
        flowTable.bind
      }
    </div>

}
