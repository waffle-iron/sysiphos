package com.flowtick.sysiphos.ui.schedule

import com.flowtick.sysiphos.scheduler.FlowScheduleDetails
import com.flowtick.sysiphos.ui.{ HtmlComponent, Layout }
import com.thoughtworks.binding.Binding.{ SingletonBindingSeq, Vars }
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.{ Div, Table, TableRow }
import org.scalajs.dom.raw.{ Event, HTMLInputElement }

import scala.scalajs.js.Date

class SchedulesComponent(flowId: Option[String], circuit: SchedulesCircuit) extends HtmlComponent with Layout {
  val schedules: Vars[FlowScheduleDetails] = Vars.empty[FlowScheduleDetails]

  def formatDate(epochSeconds: Long): String = {
    val date = new Date(epochSeconds * 1000)
    s"${date.toDateString()}, ${date.toTimeString()}"
  }

  override def init: Unit = {
    circuit.subscribe(circuit.zoom(identity)) { model =>
      schedules.value.clear()
      schedules.value ++= model.value.schedules
    }

    circuit.dispatch(LoadSchedules(flowId))
  }

  @dom
  def scheduleRow(schedule: FlowScheduleDetails): Binding[TableRow] =
    <tr>
      <td>
        {
          if (schedule.enabled.getOrElse(false)) {
            <button type="button" class="btn btn-lg btn-default active" onclick={ _: Event => circuit.dispatch(ToggleEnabled(schedule.id, enabled = false)) }>
              <i class="fas fa-toggle-on"></i>
              On
            </button>
          } else
            <button type="button" class="btn btn-lg btn-danger" onclick={ _: Event => circuit.dispatch(ToggleEnabled(schedule.id, enabled = true)) }>
              <i class="fas fa-toggle-off"></i>
              Off
            </button>
        }
      </td>
      <td>{ schedule.id }</td>
      <td><a href={ "#/flow/show/" + schedule.flowDefinitionId }> { schedule.flowDefinitionId }</a></td>
      <td>
        <input type="text" value={ schedule.expression.getOrElse("") } onblur={ (e: Event) => circuit.dispatch(SetExpression(schedule.id, e.target.asInstanceOf[HTMLInputElement].value)) }></input>
      </td>
      <td>{ schedule.nextDueDate.map(formatDate).getOrElse("N/A") }</td>
    </tr>

  @dom
  def schedulesTable: Binding[Table] = {
    <table class="table table-striped">
      <thead>
        <th></th>
        <th>ID</th>
        <th>Flow</th>
        <th>Expression</th>
        <th>Next Due</th>
      </thead>
      <tbody>
        {
          val rows = for (schedule <- schedules) yield scheduleRow(schedule).bind

          if (rows.length.bind == 0)
            SingletonBindingSeq(emptyRow).bind
          else rows.bind
        }
      </tbody>
    </table>
  }

  @dom
  def emptyRow: Binding[TableRow] =
    <tr>
      <td data:colspan="5">
        <span>No schedules yet</span>
        {
          if (flowId.nonEmpty) {
            <span>for flow { flowId.getOrElse("") }</span>
          } else <!-- -->
        }
      </td>
    </tr>

  @dom
  override def element: Binding[Div] =
    <div id="schedules">
      <h3>Schedules</h3>
      {
        flowId match {
          case Some(id) =>
            <button class="btn btn-default" type="button" onclick={ _: Event => circuit.dispatch(CreateSchedule(id, "0 * * * *")) }><i class="fas fa-plus"></i> Add</button>
          case None =>
            <!-- no schedules yet -->
        }
      }
      <!-- Table -->
      {
        schedulesTable.bind
      }
    </div>
}
