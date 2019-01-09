package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.flow.{ FlowTaskInstanceDetails, FlowTaskInstanceStatus }
import com.flowtick.sysiphos.ui.{ FlowInstanceOverview, SysiphosApi }
import diode.ActionResult.{ EffectOnly, ModelUpdate }
import diode.{ Action, Circuit, Effect }

import scala.concurrent.ExecutionContext.Implicits.global

final case class InstanceModel(overview: Option[FlowInstanceOverview])

final case class LoadInstance(instanceId: String) extends Action
final case class FoundInstance(instance: Option[FlowInstanceOverview]) extends Action
final case class RetryTask(task: FlowTaskInstanceDetails) extends Action
final case class TaskUpdated(task: FlowTaskInstanceDetails) extends Action

class ShowInstanceCircuit(api: SysiphosApi) extends Circuit[InstanceModel] {
  import com.flowtick.sysiphos.ui.vendor.ToastrSupport._

  override protected def initialModel: InstanceModel = InstanceModel(None)

  override protected def actionHandler: HandlerFunction = {
    case (model: InstanceModel, action) =>
      action match {
        case LoadInstance(instanceId) =>
          val loadInstance = api
            .getInstanceOverview(instanceId)
            .map(overview => FoundInstance(overview))
            .notifyError

          Some(EffectOnly(Effect(loadInstance)))

        case FoundInstance(overview) =>
          Some(ModelUpdate(model.copy(overview = overview)))

        case RetryTask(taskInstance) =>
          val updated = api
            .setTaskStatus(taskInstance.id, FlowTaskInstanceStatus.Retry, 3)
            .map(_ => TaskUpdated(taskInstance))
            .notifyError
            .successMessage(updated => s"triggered retry for ${updated.task.taskId} (${updated.task.id}))")

          Some(EffectOnly(Effect(updated)))

        case _ => None
      }
  }
}
