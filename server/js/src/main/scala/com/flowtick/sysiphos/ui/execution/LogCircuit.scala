package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.ui.SysiphosApi
import diode.ActionResult.{ EffectOnly, ModelUpdate }
import diode.{ Action, Circuit, Effect }

case class LogModel(log: Option[String])

case class LoadLog(logId: String) extends Action
case class FoundLog(log: String) extends Action

import scala.concurrent.ExecutionContext.Implicits.global

class LogCircuit(api: SysiphosApi) extends Circuit[LogModel] {
  override protected def initialModel: LogModel = LogModel(None)

  override protected def actionHandler: HandlerFunction = {
    case (model: LogModel, action) => action match {
      case LoadLog(logId) =>
        val getLog = Effect(api.getLog(logId).map(FoundLog))
        Some(EffectOnly(getLog))
      case FoundLog(log) => Some(ModelUpdate(model.copy(log = Some(log))))
    }

  }
}
