package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.flow.FlowInstance
import com.flowtick.sysiphos.ui.SysiphosApi
import diode.Circuit

case class InstancesModel(executions: Seq[FlowInstance])

class FlowInstancesCircuit(sysiphosApi: SysiphosApi) extends Circuit[InstancesModel] {
  override protected def initialModel: InstancesModel = InstancesModel(Seq.empty)

  override protected def actionHandler: HandlerFunction = {
    case (model: InstancesModel, action) =>
      action match {
        case _ => None
      }
  }
}
