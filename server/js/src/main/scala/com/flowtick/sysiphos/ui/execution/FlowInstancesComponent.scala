package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.flow.FlowInstanceStatus.FlowInstanceStatus
import com.flowtick.sysiphos.flow.{ FlowInstance, FlowInstanceQuery, FlowInstanceStatus }
import com.flowtick.sysiphos.ui.util.DateSupport
import com.flowtick.sysiphos.ui.{ HtmlComponent, Layout }
import com.thoughtworks.binding.Binding.{ Constants, Var, Vars }
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.{ Div, Table, TableRow }
import org.scalajs.dom.raw.{ Event, HTMLInputElement }
import org.scalajs.dom.window

import scala.util.Try

class FlowInstancesComponent(
  flowIdParam: Option[String],
  statusCsvParam: Option[String],
  startDateParam: Option[String],
  endDateParam: Option[String],
  circuit: FlowInstancesCircuit) extends HtmlComponent
  with Layout
  with DateSupport {
  val instances: Vars[FlowInstance] = Vars.empty[FlowInstance]

  val dateFieldFormat = "YYYY-MM-DD"

  val flowId: Var[Option[String]] = Var(flowIdParam)
  val startDate: Var[String] = Var(startDateParam.getOrElse(now().subtract(7, "days").format(dateFieldFormat)))
  val endDate: Var[String] = Var(endDateParam.getOrElse(now().add(7, "days").format(dateFieldFormat)))
  val statuses: Vars[FlowInstanceStatus] = statusCsvParam
    .map(_.split(",").map(FlowInstanceStatus.withName).toSeq)
    .map(statuses => Vars.apply(statuses: _*))
    .getOrElse(Vars.empty)

  override def init: Unit = {
    circuit.subscribe(circuit.zoom(identity)) { model =>
      instances.value.clear()
      instances.value ++= model.value.instances
    }

    loadInstances()
  }

  def loadInstances(): Unit = circuit.dispatch(LoadInstances(FlowInstanceQuery(
    flowDefinitionId = flowId.value.filter(_.nonEmpty),
    None,
    status = if (statuses.value.nonEmpty) Some(statuses.value) else None,
    createdGreaterThan = parseDate(startDate.value),
    createdSmallerThan = parseDate(endDate.value))))

  def deleteInstance(flowInstanceId: String): Unit = {
    if (window.confirm(s"Do you really want to delete instance $flowInstanceId")) {
      circuit.dispatch(DeleteInstances(flowInstanceId))
    }
  }

  @dom
  def instanceRow(flowInstance: FlowInstance): Binding[TableRow] =
    <tr>
      <td><a href={ "#/flow/show/" + flowInstance.flowDefinitionId }> { flowInstance.flowDefinitionId }</a></td>
      <td><a href={ "#/instances/show/" + flowInstance.id }> { flowInstance.id }</a></td>
      <td>{ formatDate(flowInstance.creationTime) }</td>
      <td style="vertical-align: middle;">{ FlowInstanceStatusHelper.instanceStatusLabel(flowInstance.status).bind }</td>
      {
        if (flowInstance.status != FlowInstanceStatus.Running)
          <td><a class="btn btn-danger" onclick={ (_: Event) => deleteInstance(flowInstance.id) } data:data-tooltip="delete instance"><i class="fa fa-trash"></i></a></td>
        else
          <td><a class="btn btn-danger" data:disabled="" data:data-tooltip="delete instance"><i class="fa fa-trash"></i></a></td>
      }
    </tr>

  @dom
  def instancesTable: Binding[Table] = {
    <table class="table table-striped">
      <thead>
        <th>Flow ID</th>
        <th>ID</th>
        <th>Created</th>
        <th>Status</th>
        <th>Actions</th>
      </thead>
      <tbody>
        {
          for (instance <- instances) yield instanceRow(instance).bind
        }
      </tbody>
    </table>
  }

  def currentViewHash(
    flowIdFilter: Option[String],
    statusesFilter: Option[Seq[FlowInstanceStatus]],
    startDateFilter: Option[String],
    endDateFilter: Option[String]): String = {
    val startDateQueryPart = startDateFilter.map(startDate => s"startDate=$startDate").getOrElse("")
    val endDateQueryPart = endDateFilter.map(endDate => s"endDate=$endDate").getOrElse("")
    val flowIdQueryPart = flowIdFilter.map(flowId => s"flowId=$flowId").getOrElse("")
    val statusQueryPart = statusesFilter.filter(_.nonEmpty).map(statuses => s"status=${statuses.mkString(",")}").getOrElse("")
    s"#/instances?$flowIdQueryPart&$statusQueryPart&$startDateQueryPart&$endDateQueryPart"
  }

  def updatePath: Event => Unit = (_: Event) => {
    org.scalajs.dom.window.location.hash = currentViewHash(flowId.value, Some(statuses.value), Some(startDate.value), Some(endDate.value))
  }

  def toggleStatus(status: FlowInstanceStatus, isActive: Boolean): Event => Unit = event => {
    if (isActive) {
      statuses.value -= status
    } else statuses.value += status

    updatePath(event)
  }

  @dom
  override def element: Binding[Div] =
    <div id="instances">
      <h3>Instances</h3>
      <div class="row">
        <div class="col-lg-6 col-md-12">
          <div class="panel panel-default">
            <div class="panel-heading">Filter</div>
            <div class="panel-body">
              <form onsubmit={ updatePath }>
                <div class="form-group">
                  <div class="input-group">
                    <label for="flow-id">Flow ID</label>
                    <input id="flow-id" type="text" class="form-control" placeholder="Flow ID to filter on" onchange={ (e: Event) => Try(flowId.value= Some(e.target.asInstanceOf[HTMLInputElement].value)) } value={ flowId.bind.getOrElse("") } onblur={ updatePath }></input>
                  </div>
                </div>
                <div class="form-group">
                  <div class="input-group">
                    <label for="start-date">Created After</label>
                    <input id="start-date" type="date" class="form-control" placeholder="Range Start" onchange={ (e: Event) => Try(startDate.value= e.target.asInstanceOf[HTMLInputElement].value) } value={ startDate.bind.toString } onblur={ updatePath }></input>
                  </div>
                </div>
                <div class="form-group">
                  <div class="input-group">
                    <label for="end-date">Created Before</label>
                    <input id="end-date" type="date" class="form-control" placeholder="Range End" onchange={ (e: Event) => Try(endDate.value= e.target.asInstanceOf[HTMLInputElement].value) } value={ endDate.bind.toString } onblur={ updatePath }></input>
                  </div>
                </div>
                <div class="form-group">
                  <div class="input-group">
                    <div class="btn-group" data:role="group" data:aria-label="status-buttons" style="display:flex">
                      {
                        for (status <- Constants(FlowInstanceStatus.values.toSeq: _*)) yield {
                          val (isActive, activeClass) = if (statuses.value.contains(status)) (true, "active") else (false, "")
                          val buttonClass = FlowInstanceStatusHelper.instanceStatusButtonClass(FlowInstanceStatus.withName(status.toString))

                          <a onclick={ toggleStatus(status, isActive) } class={ s"$buttonClass $activeClass" }>{ status.toString }</a>
                        }
                      }
                    </div>
                  </div>
                </div>
                <button class="btn btn-default" type="button" onclick={ updatePath }><i class="fas fa-redo"></i> Reload</button>
              </form>
            </div>
          </div>
        </div>
      </div>
      <div class="row">
        <div class="col-lg-12">
          <div class="panel panel-default">
            <div class="panel-body">
              { instancesTable.bind }
            </div>
            <div class="panel-footer"></div>
          </div>
        </div>
      </div>
    </div>

}
