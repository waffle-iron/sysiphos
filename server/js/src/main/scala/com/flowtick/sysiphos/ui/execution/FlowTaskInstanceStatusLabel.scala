package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.flow.FlowTaskInstanceStatus
import com.flowtick.sysiphos.flow.FlowTaskInstanceStatus.FlowTaskInstanceStatus
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Span

trait FlowTaskInstanceStatusLabel {
  @dom
  def taskStatusLabel(taskStatus: FlowTaskInstanceStatus): Binding[Span] = {
    val labelType = taskStatus match {
      case FlowTaskInstanceStatus.New => "label-info"
      case FlowTaskInstanceStatus.Running | FlowTaskInstanceStatus.Retry => "label-warning"
      case FlowTaskInstanceStatus.Done => "label-success"
      case FlowTaskInstanceStatus.Failed => "label-danger"
      case _ => "label-default"
    }
    <span class={ s"label $labelType lb-md" }>{ taskStatus.toString }</span>
  }

}
