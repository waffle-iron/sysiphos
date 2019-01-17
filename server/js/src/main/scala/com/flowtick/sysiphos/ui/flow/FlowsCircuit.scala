package com.flowtick.sysiphos.ui.flow

import com.flowtick.sysiphos.flow.FlowDefinitionSummary
import com.flowtick.sysiphos.ui.SysiphosApi
import diode.ActionResult.{ EffectOnly, ModelUpdate }
import diode.{ Action, Circuit, Effect }
import com.flowtick.sysiphos.ui.vendor.ToastrSupport._

import scala.concurrent.ExecutionContext.Implicits.global

case class FlowsModel(flowDefinitions: Seq[FlowDefinitionSummary])

case object LoadFlows extends Action
case class LoadedDefinitions(flowDefinitions: Seq[FlowDefinitionSummary]) extends Action
case class DeleteFlow(flowId: String) extends Action

class FlowsCircuit(api: SysiphosApi) extends Circuit[FlowsModel] {
  override protected def initialModel: FlowsModel = FlowsModel(Seq.empty)

  override protected def actionHandler: HandlerFunction = {
    case (model: FlowsModel, action) => action match {
      case LoadFlows =>
        val loadDefinitions = api
          .getFlowDefinitions
          .map(_.definitions)
          .map(LoadedDefinitions)
          .notifyError

        Some(EffectOnly(Effect(loadDefinitions)))

      case LoadedDefinitions(flows) =>
        Some(ModelUpdate(model.copy(flowDefinitions = flows)))

      case DeleteFlow(flowId) =>
        val deleteFlow = api
          .deleteFlowDefinition(flowId)
          .notifyError
          .successMessage(_ => s"deleted flow definition $flowId")
          .map(_ => LoadFlows)

        Some(EffectOnly(Effect(deleteFlow)))

      case _ => None
    }
  }
}
