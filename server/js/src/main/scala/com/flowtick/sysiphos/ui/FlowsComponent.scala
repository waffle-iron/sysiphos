package com.flowtick.sysiphos.ui

import com.thoughtworks.binding.Binding.{ Constants, Vars }
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.{ Div, Table, TableRow }

import scala.concurrent.ExecutionContext.Implicits.global

class FlowsComponent(sysiphosApi: SysiphosApi) extends HtmlComponent with Layout {
  sealed trait Action
  case object Initial extends Action
  case object Foo extends Action
  case class SetDefinitions(definitions: Seq[FlowDefinitionSummary]) extends Action

  val flows = Vars.empty[Seq[FlowDefinitionSummary]]

  override def init(): Unit = getDefinition()

  def getDefinition(): Unit = sysiphosApi.getFlowDefinitions.foreach {
    case Right(response) =>
      flows.value.clear()
      flows.value += response.data.definitions
    case Left(error) => println(error)
  }

  @dom
  def flowTable(flowsSummary: Seq[FlowDefinitionSummary]): Binding[Table] = {
    <table>
      {
        Constants(flowsSummary: _*).map { flow => flowRow(flow).bind }
      }
    </table>
  }

  @dom
  def flowRow(flow: FlowDefinitionSummary): Binding[TableRow] =
    <tr>
      <td>{ flow.id.toString }</td>
    </tr>

  @dom
  def flowSection: Binding[Div] = {
    <div>
      <h3>Flows</h3>
      {
        for (flowList <- flows) yield {
          flowTable(flowList).bind
        }
      }
    </div>
  }

  @dom
  override val element: Binding[Div] = {
    <div>
      { layout(flowSection.bind).bind }
    </div>
  }

}
