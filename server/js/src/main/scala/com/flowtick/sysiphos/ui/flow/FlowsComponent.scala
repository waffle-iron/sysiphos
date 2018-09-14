package com.flowtick.sysiphos.ui.flow

import com.flowtick.sysiphos.flow.{ FlowDefinitionSummary, FlowInstanceStatus, InstanceCount }
import com.flowtick.sysiphos.ui.vendor.ToastrSupport._
import com.flowtick.sysiphos.ui.{ HtmlComponent, Layout, SysiphosApi }
import com.thoughtworks.binding.Binding.{ Constants, Vars }
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html._

import scala.concurrent.ExecutionContext.Implicits.global

class FlowsComponent(sysiphosApi: SysiphosApi) extends HtmlComponent with Layout {
  val flows: Vars[FlowDefinitionSummary] = Vars.empty[FlowDefinitionSummary]

  def loadDefinitions: Binding[Vars[FlowDefinitionSummary]] = Binding {
    sysiphosApi.getFlowDefinitions.notifyError.foreach { response =>
      flows.value.clear()
      flows.value.append(response.definitions: _*)
    }

    flows
  }

  @dom
  def instanceCountButton(count: InstanceCount): Binding[Anchor] =
    <a href={ s"#/instances/filter/${count.flowDefinitionId}?status=${count.status}" } class={
      FlowInstanceStatus.withName(count.status) match {
        case FlowInstanceStatus.Scheduled | FlowInstanceStatus.ManuallyTriggered => "btn btn-info"
        case FlowInstanceStatus.Running => "btn btn-warning"
        case FlowInstanceStatus.Failed => "btn btn-danger"
        case FlowInstanceStatus.Done => "btn btn-success"
        case _ => "btn btn-default"
      }
    }><strong>{ count.status }</strong>&nbsp;<span class="badge"> { count.count.toString } </span></a>

  @dom
  def flowRow(flow: FlowDefinitionSummary): Binding[TableRow] =
    <tr>
      <td><a href={ "#/flow/show/" + flow.id }> { flow.id }</a></td>
      <td>
        <div class="btn-group" data:role="group" data:aria-label="count-buttons">
          { Constants(flow.counts: _*).map(instanceCountButton(_).bind) }
          <a class="btn btn-default" href={ s"#/instances/filter/${flow.id}" }>
            <strong>all</strong>
            <span class="badge"><i class="fas fa-search"></i></span>
          </a>
        </div>
      </td>
      <td>
        <a href={ s"/graphiql?query=mutation%20%7B%0A%09createInstance(flowDefinitionId%3A%20%22${flow.id}%22%2C%20context%3A%20%5B%0A%20%20%20%20%7Bkey%3A%20%22somekey%22%2C%20value%3A%20%22somevalue%22%7D%2C%0A%20%20%20%20%7Bkey%3A%20%22somekey2%22%2C%20value%3A%20%22somevalue2%22%7D%0A%20%20%5D)%20%7B%0A%20%20%20%20id%2C%20context%20%7Bvalue%7D%0A%20%20%7D%0A%7D" } class="btn btn-success"><i class="glyphicon glyphicon-play"></i></a>
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
          for (flow <- loadDefinitions.bind) yield flowRow(flow).bind
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
