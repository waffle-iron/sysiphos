package com.flowtick.sysiphos.ui.flow

import com.flowtick.sysiphos.flow.FlowDefinitionDetails
import com.flowtick.sysiphos.ui.SysiphosApi
import com.flowtick.sysiphos.ui.vendor.Toastr
import com.flowtick.sysiphos.ui.vendor.ToastrSupport._
import diode.ActionResult.{ EffectOnly, ModelUpdate }
import diode.{ Action, Circuit, Effect, NoAction }
import org.scalajs.dom.window

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class FlowModel(definition: Option[FlowDefinitionDetails], source: Option[String])

case object ResetSource extends Action
case class LoadDefinition(id: String) extends Action
case class FoundDefinition(definition: Option[FlowDefinitionDetails]) extends Action
case class CreateOrUpdate(source: String) extends Action
case class SetSource(sourceValue: String) extends Action

class FlowCircuit(api: SysiphosApi) extends Circuit[FlowModel] {
  override protected def initialModel: FlowModel = FlowModel(None, None)

  override protected def actionHandler: HandlerFunction = {
    case (model: FlowModel, action) =>
      action match {
        case ResetSource =>
          val setEmptySource = Effect(Future.successful(SetSource("")))
          Some(EffectOnly(setEmptySource))

        case SetSource(newSource) =>
          Some(ModelUpdate(model.copy(source = Some(newSource))))

        case LoadDefinition(id) =>
          val loadDefinition = Effect(api.getFlowDefinition(id).map(FoundDefinition))
          Some(EffectOnly(loadDefinition))

        case CreateOrUpdate(source) =>
          Some(EffectOnly(Effect(createOrUpdate(source).map(_ => NoAction))))

        case FoundDefinition(someDefinition) =>
          Some(ModelUpdate(model.copy(definition = someDefinition, source = someDefinition.flatMap(_.source))))
      }
  }

  def createOrUpdate(source: String): Future[Unit] =
    api
      .createOrUpdateFlowDefinition(source)
      .successMessage(_ => "Flow updated.")
      .map {
        case Some(details) =>
          window.location.hash = s"#/flow/show/${details.id}"
        case None =>
          Toastr.warning("nothing created")
      }

}
