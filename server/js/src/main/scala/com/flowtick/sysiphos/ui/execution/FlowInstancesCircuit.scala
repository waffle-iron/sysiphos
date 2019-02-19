package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.flow.{ FlowInstanceDetails, FlowInstanceQuery }
import com.flowtick.sysiphos.ui.SysiphosApi
import com.flowtick.sysiphos.ui.util.DateSupport
import diode.ActionResult.{ EffectOnly, ModelUpdate }
import diode.{ Action, Circuit, Effect }
import com.flowtick.sysiphos.ui.vendor.ToastrSupport._

import scala.concurrent.ExecutionContext.Implicits.global

final case class InstancesModel(instances: Seq[FlowInstanceDetails])

final case class LoadInstances(query: FlowInstanceQuery) extends Action
final case class FoundInstances(instances: Seq[FlowInstanceDetails]) extends Action
final case class DeleteInstances(flowInstanceId: String) extends Action

class FlowInstancesCircuit(api: SysiphosApi) extends Circuit[InstancesModel] with DateSupport {
  override protected def initialModel: InstancesModel = InstancesModel(Seq.empty)

  override protected def actionHandler: HandlerFunction = {
    case (model: InstancesModel, action) =>
      action match {
        case LoadInstances(query) =>
          val loadInstances = Effect(api.getInstances(query).map(list => FoundInstances(list.instances)))
          Some(EffectOnly(loadInstances))

        case FoundInstances(newInstances) =>
          Some(ModelUpdate(model.copy(instances = newInstances)))

        case DeleteInstances(flowInstanceId) =>
          val notDeletedInstances = api
            .deleteInstance(flowInstanceId)
            .map(deletedInstanceId => FoundInstances(model.instances.filter(_.id != deletedInstanceId)))
            .successMessage(_ => s"deleted instance $flowInstanceId")

          Some(EffectOnly(Effect(notDeletedInstances)))

        case other: Any =>
          println(s"unhandled action: $other")
          None
      }
  }
}
