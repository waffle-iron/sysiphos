package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.ui.{ FlowInstanceOverview, SysiphosApi }
import diode.ActionResult.{ EffectOnly, ModelUpdate }
import diode.{ Action, Circuit, Effect }
import scala.concurrent.ExecutionContext.Implicits.global

final case class InstanceModel(overview: Option[FlowInstanceOverview])

final case class LoadInstance(instanceId: String) extends Action
final case class FoundInstance(instance: Option[FlowInstanceOverview]) extends Action

class ShowInstanceCircuit(api: SysiphosApi) extends Circuit[InstanceModel] {
  override protected def initialModel: InstanceModel = InstanceModel(None)

  override protected def actionHandler: HandlerFunction = {
    case (model: InstanceModel, action) =>
      action match {
        case LoadInstance(instanceId) =>
          val loadInstance = Effect(api.getInstanceOverview(instanceId).map(overview => FoundInstance(overview)))
          Some(EffectOnly(loadInstance))

        case FoundInstance(overview) =>
          Some(ModelUpdate(model.copy(overview = overview)))

        case other: Any =>
          println(s"unhandled action: $other")
          None
      }
  }
}
