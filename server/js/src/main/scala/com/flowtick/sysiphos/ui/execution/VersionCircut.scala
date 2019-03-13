package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.ui.SysiphosApi
import diode.ActionResult.{ EffectOnly, ModelUpdate }
import diode.{ Action, Circuit, Effect }

import scala.concurrent.ExecutionContext.Implicits.global

class VersionCircuit(api: SysiphosApi) extends Circuit[VersionModel] {
  override protected def initialModel: VersionModel = VersionModel(None)

  override protected def actionHandler: HandlerFunction = {
    case (model: VersionModel, action) => action match {
      case GetVersion =>
        val version = Effect(api.getVersion.map(FoundVersion))
        Some(EffectOnly(version))
      case FoundVersion(version) =>
        Some(ModelUpdate(model.copy(version = Option(version))))
    }
  }
}

case class VersionModel(version: Option[String])
case class FoundVersion(version: String) extends Action
case object GetVersion extends Action
