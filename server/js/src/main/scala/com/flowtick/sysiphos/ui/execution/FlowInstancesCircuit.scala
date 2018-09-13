package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.flow.{ FlowInstance, FlowInstanceDetails }
import com.flowtick.sysiphos.ui.SysiphosApi
import com.flowtick.sysiphos.ui.util.DateSupport
import diode.ActionResult.{ EffectOnly, ModelUpdate }
import diode.{ Action, Circuit, Effect }

import scala.concurrent.ExecutionContext.Implicits.global

final case class InstancesModel(instances: Seq[FlowInstance])

final case class LoadInstances(flowId: Option[String], status: Option[String], hoursBack: Int) extends Action
final case class FoundInstances(instances: Seq[FlowInstanceDetails]) extends Action

class FlowInstancesCircuit(api: SysiphosApi) extends Circuit[InstancesModel] with DateSupport {
  override protected def initialModel: InstancesModel = InstancesModel(Seq.empty)

  override protected def actionHandler: HandlerFunction = {
    case (model: InstancesModel, action) =>
      action match {
        case LoadInstances(flowId, status, hoursBack) =>
          val loadInstances = Effect(api.getInstances(flowId, status, Some(nowMinusHours(hoursBack))).map(list => FoundInstances(list.instances)))
          Some(EffectOnly(loadInstances))

        case FoundInstances(newInstances) =>
          Some(ModelUpdate(model.copy(instances = newInstances)))

        case other: Any =>
          println(s"unhandled action: $other")
          None
      }
  }
}
