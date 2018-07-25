package com.flowtick.sysiphos.ui

import com.flowtick.sysiphos.flow.FlowDefinitionSummary
import com.flowtick.sysiphos.ui.vendor.ToastrSupport._
import com.thoughtworks.binding.Binding.Vars
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.{ Div, Table, TableRow }

import scala.concurrent.ExecutionContext.Implicits.global

class ExecutionsComponent(sysiphosApi: SysiphosApi) extends HtmlComponent with Layout {
  val executions: Vars[FlowDefinitionSummary] = Vars.empty[FlowDefinitionSummary]

  def loadExecutions(): Unit = Binding {
    sysiphosApi.getFlowDefinitions.notifyError.foreach { response =>
      executions.value.clear()
      executions.value.append(response.data.definitions: _*)
    }
    executions
  }

  override def init(): Unit = loadExecutions()

  @dom
  def executionsRow(flow: FlowDefinitionSummary): Binding[TableRow] =
    <tr>
      <td><a href={ "#/flow/show/" + flow.id }> { flow.id }</a></td>
    </tr>

  @dom
  def executionsTable: Binding[Table] = {
    <table class="table table-striped">
      <thead>
        <th>ID</th>
        <th>Actions</th>
      </thead>
      <tbody>
        {
          for (execution <- executions) yield executionsRow(execution).bind
        }
      </tbody>
    </table>
  }

  @dom
  override def element: Binding[Div] =
    <div id="executions">
      <h3>Executions</h3>
      {
        executionsTable.bind
      }
    </div>

}
