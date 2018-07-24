package com.flowtick.sysiphos.ui

import com.flowtick.sysiphos.scheduler.FlowScheduleDetails
import com.flowtick.sysiphos.ui.vendor.ToastrSupport._
import com.thoughtworks.binding.Binding.Vars
import com.thoughtworks.binding.{ Binding, FutureBinding, dom }
import org.scalajs.dom.html.{ Div, Table, TableRow }
import org.scalajs.dom.raw.{ Event, HTMLInputElement }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.Date

class SchedulesComponent(flowId: Option[String], api: SysiphosApi) extends HtmlComponent with Layout {
  val schedules: Vars[FlowScheduleDetails] = Vars.empty[FlowScheduleDetails]

  def getSchedules(): Unit = api.getSchedules(flowId).notifyError.foreach { response =>
    schedules.value.clear()
    schedules.value.append(response.data.schedules: _*)
  }

  override def init(): Unit = getSchedules()

  def formatDate(epochSeconds: Long): String = {
    val date = new Date(epochSeconds * 1000)
    s"${date.toDateString()}, ${date.toTimeString()}"
  }

  def toggleEnable(id: String, enabled: Boolean): Unit =
    api
      .setFlowScheduleEnabled(id, enabled)
      .notifyError
      .successMessage(_ => s"$id is now ${if (enabled) "enabled" else "disabled"}")
      .foreach(_ => getSchedules)

  def setExpression(id: String, expression: String): Unit = {
    api
      .setFlowScheduleExpression(id, expression)
      .notifyError
      .successMessage(_ => s"$id expression updated: $expression")
      .foreach(_ => getSchedules)
  }

  @dom
  def scheduleRow(schedule: FlowScheduleDetails): Binding[TableRow] =
    <tr>
      <td>
        {
          if (schedule.enabled.getOrElse(false)) {
            <button type="button" class="btn btn-lg btn-default active" onclick={ _: Event => toggleEnable(schedule.id, enabled = false) }>
              <i class="fas fa-toggle-on"></i>
              On
            </button>
          } else
            <button type="button" class="btn btn-lg btn-danger" onclick={ _: Event => toggleEnable(schedule.id, enabled = true) }>
              <i class="fas fa-toggle-off"></i>
              Off
            </button>
        }
      </td>
      <td>{ schedule.id }</td>
      <td><a href={ "#/flow/show/" + schedule.flowDefinitionId }> { schedule.flowDefinitionId }</a></td>
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
  override def element: Binding[Div] =
    <div>
      { layout(schedulesSection.bind).bind }
    </div>
}
