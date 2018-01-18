package com.flowtick.sysiphos.ui

import mhtml.{ Rx, Var }
import scala.xml.Elem
import scala.concurrent.ExecutionContext.Implicits.global

class FlowsComponent(sysiphosApi: SysiphosApi) extends HtmlComponent with Layout {
  sealed trait Action
  case object Initial extends Action
  case object Foo extends Action
  case class SetDefinitions(definitions: Seq[FlowDefinitionSummary]) extends Action

  case class State(flows: Seq[FlowDefinitionSummary])

  val action: Var[Action] = Var(Initial)

  val state: Rx[State] = action.foldp(State(Seq.empty))(update).impure.sharing

  override def init(): Unit = getDefinition()

  def getDefinition(): Unit = sysiphosApi.getFlowDefinitions.foreach {
    case Right(response) => action := SetDefinitions(response.data.definitions)
    case Left(error) => println(error)
  }

  def update(current: State, action: Action): State = {
    println(s"updating $current, $action")

    action match {
      case SetDefinitions(flows) => current.copy(flows = flows)
      case _ =>
        println(s"ignoring unknown $action")
        current
    }
  }

  def flowTable(flows: Seq[FlowDefinitionSummary]): Elem =
    <table>
      { flows.map(flowRow) }
    </table>

  def flowRow(flow: FlowDefinitionSummary): Elem =
    <tr>
      <td>{ flow.id }</td>
    </tr>

  override val element: Elem = layout {
    <div>
      <h3>Flows</h3>
      { state.map { current => flowTable(current.flows) } }
    </div>
  }
}
