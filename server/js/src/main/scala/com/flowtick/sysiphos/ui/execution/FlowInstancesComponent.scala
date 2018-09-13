package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.flow.FlowInstance
import com.flowtick.sysiphos.ui.util.DateSupport
import com.flowtick.sysiphos.ui.{ HtmlComponent, Layout }
import com.thoughtworks.binding.Binding.{ Var, Vars }
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.{ Div, Table, TableRow }
import org.scalajs.dom.raw.{ Event, HTMLInputElement }

import scala.util.Try

class FlowInstancesComponent(
  flowId: Option[String],
  status: Option[String],
  circuit: FlowInstancesCircuit) extends HtmlComponent
  with Layout
  with DateSupport
  with FlowInstanceStatusLabel {
  val instances: Vars[FlowInstance] = Vars.empty[FlowInstance]
  val hoursBack: Var[Int] = Var(24)

  override def init: Unit = {
    circuit.subscribe(circuit.zoom(identity)) { model =>
      instances.value.clear()
      instances.value ++= model.value.instances
    }

    loadInstances()
  }

  def loadInstances(): Unit = circuit.dispatch(LoadInstances(flowId, status, hoursBack.value))

  @dom
  def instanceRow(flowInstance: FlowInstance): Binding[TableRow] =
    <tr>
      <td><a href={ "#/flow/show/" + flowInstance.flowDefinitionId }> { flowInstance.flowDefinitionId }</a></td>
      <td><a href={ "#/instances/show/" + flowInstance.id }> { flowInstance.id }</a></td>
      <td>{ formatDate(flowInstance.creationTime) }</td>
      <td>{ instanceStatusLabel(flowInstance.status).bind }</td>
      <td><span>{ flowInstance.retries.toString }</span></td>
      <td></td>
    </tr>

  @dom
  def instancesTable: Binding[Table] = {
    <table class="table table-striped">
      <thead>
        <th>Flow ID</th>
        <th>ID</th>
        <th>Created</th>
        <th>Status</th>
        <th>Retries</th>
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
      <div class="row">
        <div class="col-lg-3">
          <div class="form-group">
            <label for="hours">Hours back:</label>
            <div class="input-group">
              <input id="hours" type="number" class="form-control" placeholder="Hours back..." onchange={ (e: Event) => Try(hoursBack.value= e.target.asInstanceOf[HTMLInputElement].value.toInt) } value={ hoursBack.bind.toString } onblur={ (_: Event) => loadInstances() }></input>
              <span class="input-group-btn">
                <button class="btn btn-default" type="button" onclick={ (_: Event) => loadInstances() }>Show</button>
              </span>
            </div><!-- /input-group -->
          </div>
        </div><!-- /.col-lg-6 -->
      </div>
      <div class="row">
        <div class="col-lg-12">
          { instancesTable.bind }
        </div>
      </div>
    </div>

}
