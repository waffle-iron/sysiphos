package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.flow.FlowInstance
import com.flowtick.sysiphos.ui.{ HtmlComponent, Layout }
import com.thoughtworks.binding.Binding.Vars
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.{ Div, Table, TableRow }

class FlowInstancesComponent(circuit: FlowInstancesCircuit) extends HtmlComponent with Layout {
  val instances: Vars[FlowInstance] = Vars.empty[FlowInstance]

  override def init: Unit = {}

  @dom
  def instanceRow(flowInstance: FlowInstance): Binding[TableRow] =
    <tr>
      <td><a href={ "#/instances/show/" + flowInstance.id }> { flowInstance.id }</a></td>
    </tr>

  @dom
  def instancesTable: Binding[Table] = {
    <table class="table table-striped">
      <thead>
        <th>ID</th>
        <th>Actions</th>
      </thead>
      <tbody>
        {
          for (instance <- instances) yield instanceRow(instance).bind
        }
      </tbody>
    </table>
  }

  @dom
  override def element: Binding[Div] =
    <div id="instances">
      <h3>Instances</h3>
      {
        instancesTable.bind
      }
    </div>

}
