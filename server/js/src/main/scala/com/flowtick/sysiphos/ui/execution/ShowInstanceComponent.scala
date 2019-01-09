package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.flow.{ FlowTaskInstanceDetails, FlowTaskInstanceStatus }
import com.flowtick.sysiphos.ui.util.DateSupport
import com.flowtick.sysiphos.ui.{ FlowInstanceOverview, HtmlComponent, Layout }
import com.thoughtworks.binding.Binding.{ Constants, SingletonBindingSeq, Var }
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.Event
import org.scalajs.dom.html.{ Div, TableRow }

import scala.scalajs.js.URIUtils

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
      <td>{ taskInstanceDetails.startTime.map(formatDate).getOrElse("not started yet") }</td>
      <td>{ taskInstanceDetails.endTime.map(formatDate).getOrElse("not ended yet") }</td>
      <td>{ taskInstanceDetails.nextDueDate.map(formatDate).getOrElse("N/A") }</td>
      <td>{ taskStatusLabel(taskInstanceDetails.status).bind }</td>
      <td><span>{ taskInstanceDetails.retries.toString }</span></td>
      <td>
        <div class="btn-group btn-group" data:role="group" style="display:flex">
          <a href={ s"#/log/${URIUtils.encodeURIComponent(taskInstanceDetails.logId)}" } class="btn btn-primary">Log</a>
          {
            taskInstanceDetails.status match {
              case FlowTaskInstanceStatus.Failed | FlowTaskInstanceStatus.Retry =>
                <a onclick={ (_: Event) => circuit.dispatch(RetryTask(taskInstanceDetails)) } class="btn btn-warning"><i class="fas fa-redo"></i></a>
              case _ => <!-- retry only allowed in status failed -->
            }
          }
        </div>
      </td>
    </tr>

  @dom
  def overviewPanel(instanceOverview: FlowInstanceOverview): Binding[Div] = {
    <div class="row">
      <div class="col-lg-6">
        <div class="panel panel-default">
          <div class="panel-heading">
            <h4>
              <strong><a href={ s"#/flow/show/${instanceOverview.instance.flowDefinitionId}" }>{ instanceOverview.instance.flowDefinitionId }</a></strong>
              instance ({ instanceOverview.instance.id }
              )
            </h4>
          </div>
          <ul class="list-group">
            <li class="list-group-item">
              <p class="list-group-item-heading"><strong>Status</strong></p>
              <p class="list-group-item-text">{ instanceStatusLabel(instanceOverview.instance.status).bind }</p>
            </li>
            <li class="list-group-item">
              <p class="list-group-item-heading"><strong>Creation Time</strong></p>
              <p class="list-group-item-text">{ formatDate(instanceOverview.instance.creationTime) }</p>
            </li>
            <li class="list-group-item">
              <p class="list-group-item-heading"><strong>Start Time</strong></p>
              <p class="list-group-item-text">{ instanceOverview.instance.startTime.map(formatDate).getOrElse("not started yet") }</p>
            </li>
            <li class="list-group-item">
              <p class="list-group-item-heading"><strong>End Time</strong></p>
              <p class="list-group-item-text">{ instanceOverview.instance.endTime.map(formatDate).getOrElse("not ended yet") }</p>
            </li>
          </ul>
        </div>
      </div>
      <div class="col-lg-6">
        <div class="panel panel-default">
          <div class="panel-heading">
            <h4>Context</h4>
          </div>
          <ul class="list-group">
            {
              if (instanceOverview.instance.context.isEmpty)
                SingletonBindingSeq(Binding(<li class="list-group-item"><span>empty</span></li>))
              else Constants(instanceOverview.instance.context: _*).map(contextValue => {
                <li class="list-group-item">
                  <p class="list-group-item-heading"><strong>{ contextValue.key }</strong></p>
                  <p class="list-group-item-text">
                    { contextValue.value }
                  </p>
                </li>
              })
            }
          </ul>
        </div>
      </div>
      <div class="col-lg-12">
        <div class="panel panel-default">
          <div class="panel-heading">
            <h4>Task Instances</h4>
          </div>
          <table class="table table-striped">
            <thead>
              <th>Task ID</th>
              <th>ID</th>
              <th>Start Time</th>
              <th>End Time</th>
              <th>Due Date</th>
              <th>Status</th>
              <th>Retries Left</th>
              <th>Actions</th>
            </thead>
            <tbody>
              {
                Constants(instanceOverview.tasks: _*).map(taskRow(_).bind)
              }
            </tbody>
          </table>
        </div>
      </div>
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
