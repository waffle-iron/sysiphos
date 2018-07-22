package com.flowtick.sysiphos.ui

import com.flowtick.sysiphos.scheduler.FlowScheduleDetails
import com.thoughtworks.binding.Binding.Vars
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.{ Div, Table, TableRow }

import scala.concurrent.ExecutionContext.Implicits.global
import com.flowtick.sysiphos.ui.vendor.ToastrSupport._
import org.scalajs.dom.raw.{ Event, HTMLInputElement }

import scala.concurrent.Future
import scala.scalajs.js.Date

class SchedulesComponent(api: SysiphosApi) extends HtmlComponent with Layout {
  val schedules: Vars[FlowScheduleDetails] = Vars.empty[FlowScheduleDetails]

  def getSchedules(): Unit = api.getSchedules.notifyError.foreach { response =>
    schedules.value.clear()
    schedules.value.append(response.data.schedules: _*)
  }

  def formatDate(epochSeconds: Long): String = {
    val date = new Date(epochSeconds * 1000)
    s"${date.toDateString()}, ${date.toTimeString()}"
  }

  def toggleEnable(id: String, enabled: Boolean): Unit =
    api
      .setFlowScheduleEnabled(id, enabled)
      .notifyError
      .successMessage(_ => s"$id is now ${if (enabled) "enabled" else "disabled"}")
      .foreach(_ => getSchedules())

  def setExpression(id: String, expression: String): Unit = {
    api
      .setFlowScheduleExpression(id, expression)
      .notifyError
      .successMessage(_ => s"$id expression updated: $expression")
      .foreach(_ => getSchedules())
  }

  @dom
  def scheduleRow(schedule: FlowScheduleDetails): Binding[TableRow] =
    <tr>
      <td>
        {
          if (schedule.enabled.getOrElse(false)) {
            <button class="btn btn-danger" onclick={ _: Event => toggleEnable(schedule.id, enabled = false) }>Disable</button>
          } else
            <button class="btn btn-info" onclick={ _: Event => toggleEnable(schedule.id, enabled = true) }>Enable</button>
        }
      </td>
      <td>{ schedule.id }</td>
      <td><a href={ "#/flow/" + schedule.flowDefinitionId }> { schedule.flowDefinitionId }</a></td>
      <td>
        <input type="text" value={ schedule.expression.getOrElse("") } onblur={ (e: Event) => setExpression(schedule.id, e.target.asInstanceOf[HTMLInputElement].value) }></input>
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
          for (schedule <- schedules) yield scheduleRow(schedule).bind
        }
      </tbody>
    </table>
  }

  @dom
  def schedulesSection: Binding[Div] = {
    <div>
      <h3>Schedules</h3>
      {
        schedulesTable.bind
      }
    </div>
  }

  @dom
  override val element: Binding[Div] =
    <div>
      { getSchedules(); layout(schedulesSection.bind).bind }
    </div>
}
