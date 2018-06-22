package com.flowtick.sysiphos.ui

import com.flowtick.sysiphos.flow.FlowDefinitionSummary
import com.flowtick.sysiphos.ui.vendor.DataTablesSupport
import com.flowtick.sysiphos.ui.vendor.ToastrSupport._
import com.thoughtworks.binding.Binding.Vars
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Div

import scala.concurrent.ExecutionContext.Implicits.global

class FlowsComponent(sysiphosApi: SysiphosApi) extends HtmlComponent with Layout {
  sealed trait Action
  case object Initial extends Action
  case object Foo extends Action
  case class SetDefinitions(definitions: Seq[FlowDefinitionSummary]) extends Action

  val flows = Vars.empty[Seq[FlowDefinitionSummary]]

  override def init(): Unit = {
    getDefinitions()
  }

  def getDefinitions(): Unit = sysiphosApi.getFlowDefinitions.notifyError.foreach { response =>
    DataTablesSupport.createDataTable("#flows-table", response.data.definitions)
  }

  @dom
  def flowsSection: Binding[Div] = {
    <div>
      <h3>Flows</h3>
      <table class="table" id="flows-table">
      </table>
    </div>
  }

  @dom
  override val element: Binding[Div] = {
    <div>
      { layout(flowsSection.bind).bind }
    </div>
  }

}
