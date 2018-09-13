package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.flow.{ FlowTaskInstanceDetails, FlowTaskInstanceStatus }
import com.flowtick.sysiphos.ui.util.DateSupport
import com.flowtick.sysiphos.ui.{ FlowInstanceOverview, HtmlComponent, Layout }
import com.thoughtworks.binding.Binding.{ Constants, SingletonBindingSeq, Var }
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.{ Div, TableRow }

class ShowInstanceComponent(
  instanceId: String,
  circuit: ShowInstanceCircuit) extends HtmlComponent
  with Layout
  with DateSupport
  with FlowInstanceStatusLabel
  with FlowTaskInstanceStatusLabel {
  val overview: Var[Option[FlowInstanceOverview]] = Var(None)

  override def init: Unit = {
    circuit.subscribe(circuit.zoom(identity)) { model =>
      overview.value = model.value.overview
    }

    circuit.dispatch(LoadInstance(instanceId))
  }

  @dom
  def taskRow(taskInstanceDetails: FlowTaskInstanceDetails): Binding[TableRow] =
    <tr>
      <td>{ taskInstanceDetails.taskId }</td>
      <td>{ taskInstanceDetails.id }</td>
      <td>{ taskStatusLabel(taskInstanceDetails.status).bind }</td>
      <td><span>{ taskInstanceDetails.retries.toString }</span></td>
      <td><button class="btn btn-primary">Log</button></td>
    </tr>

  @dom
  def overviewPanel(instanceOverview: FlowInstanceOverview): Binding[Div] = {
    <div>
      <div class="panel panel-default">
        <div class="panel-heading">
          <strong>{ instanceOverview.instance.flowDefinitionId }</strong>
          definition instance ({ instanceOverview.instance.id }
          )
        </div>
        <ul class="list-group">
          <li class="list-group-item"><strong>Status:</strong> { instanceStatusLabel(instanceOverview.instance.status).bind } </li>
          <li class="list-group-item"><strong>Retries:</strong> { instanceOverview.instance.retries.toString } </li>
          <li class="list-group-item"><strong>Start Time:</strong> { instanceOverview.instance.startTime.map(formatDate).getOrElse("not started yet") }</li>
          <li class="list-group-item"><strong>End Time:</strong> { instanceOverview.instance.endTime.map(formatDate).getOrElse("not ended yet") }</li>
          <li class="list-group-item">
            <strong>Context:</strong>
            {
              if (instanceOverview.instance.context.isEmpty)
                SingletonBindingSeq(Binding(<span>empty </span>))
              else Constants(instanceOverview.instance.context: _*).map(contextValue => {
                <span class="label label-default">{ contextValue.key } : { contextValue.value }</span>
              })
            }
          </li>
        </ul>
      </div>
      <h4>Task Instances</h4>
      <table class="table table-striped">
        <thead>
          <th>Task ID</th>
          <th>ID</th>
          <th>Status</th>
          <th>Retries</th>
          <th>Actions</th>
        </thead>
        <tbody>
          {
            Constants(instanceOverview.tasks: _*).map(taskRow(_).bind)
          }
        </tbody>
      </table>
    </div>
  }

  @dom
  override def element: Binding[Div] =
    <div id="instance">
      {
        overview.bind match {
          case Some(instanceOverview) => overviewPanel(instanceOverview).bind
          case None => <div> not found </div>
        }
      }
    </div>

}
