package com.flowtick.sysiphos.ui.schedule

import com.flowtick.sysiphos.scheduler.FlowScheduleDetails
import com.flowtick.sysiphos.ui.{ FlowScheduleList, SysiphosApi }
import diode.ActionResult.{ EffectOnly, ModelUpdate, ModelUpdateEffect }
import diode.{ Action, Circuit, Effect }
import com.flowtick.sysiphos.ui.vendor.ToastrSupport._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class SchedulesModel(schedules: Seq[FlowScheduleDetails], flowId: Option[String])

case class LoadSchedules(flowId: Option[String]) extends Action
case class FoundSchedules(list: FlowScheduleList) extends Action
case class ToggleEnabled(scheduleId: String, enabled: Boolean) extends Action
case class SetExpression(scheduleId: String, expression: String) extends Action
case class CreateSchedule(flowId: String, expression: String) extends Action

class SchedulesCircuit(api: SysiphosApi) extends Circuit[SchedulesModel] {
  override protected def initialModel: SchedulesModel = SchedulesModel(Seq.empty, None)

  override protected def actionHandler: HandlerFunction = {
    case (model: SchedulesModel, action) =>
      action match {
        case LoadSchedules(flowId) =>
          val loadFuture = api.getSchedules(flowId).notifyError.map(result => FoundSchedules(result))
          Some(ModelUpdateEffect(model.copy(flowId = flowId), Effect(loadFuture)))

        case FoundSchedules(list) =>
          Some(ModelUpdate(model.copy(schedules = list.schedules)))

        case ToggleEnabled(scheduleId, enabled) =>
          val toggleFuture = Effect(toggleEnable(scheduleId, enabled).map(_ => LoadSchedules(model.flowId)))
          Some(EffectOnly(toggleFuture))

        case SetExpression(scheduleId, expression) =>
          val setFuture = Effect(setExpression(scheduleId, expression).map(_ => LoadSchedules(model.flowId)))
          Some(EffectOnly(setFuture))

        case CreateSchedule(flowId, expression) =>
          val createFuture = Effect(createSchedule(flowId, expression).map(_ => LoadSchedules(model.flowId)))
          Some(EffectOnly(createFuture))
      }
  }

  def toggleEnable(id: String, enabled: Boolean): Future[Boolean] =
    api
      .setFlowScheduleEnabled(id, enabled)
      .notifyError
      .successMessage(_ => s"$id is now ${if (enabled) "enabled" else "disabled"}")

  def setExpression(id: String, expression: String): Future[String] =
    api
      .setFlowScheduleExpression(id, expression)
      .notifyError
      .successMessage(_ => s"$id expression updated: $expression")

  def createSchedule(flowId: String, expression: String): Future[FlowScheduleDetails] =
    api
      .createFlowSchedule(flowId, expression)
      .notifyError
      .successMessage(schedule => s"schedule created: ${schedule.id}")

}
