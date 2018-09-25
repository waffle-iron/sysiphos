package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.flow.FlowInstanceStatus
import com.flowtick.sysiphos.flow.FlowInstanceStatus.FlowInstanceStatus
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Span

trait FlowInstanceStatusLabel {
  @dom
  def instanceStatusLabel(instanceStatus: FlowInstanceStatus): Binding[Span] = {
    val labelType = instanceStatus match {
      case FlowInstanceStatus.Scheduled | FlowInstanceStatus.Triggered => "label-info"
      case FlowInstanceStatus.Running => "label-warning"
      case FlowInstanceStatus.Done => "label-success"
      case FlowInstanceStatus.Failed => "label-danger"
      case _ => "label-default"
    }
    <span class={ s"label $labelType lb-md" }>{ instanceStatus.toString }</span>
  }

}
