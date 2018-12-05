package com.flowtick.sysiphos.ui.flow

import com.flowtick.sysiphos.flow.FlowDefinitionDetails
import com.flowtick.sysiphos.ui._
import com.flowtick.sysiphos.ui.schedule.SchedulesComponent
import com.flowtick.sysiphos.ui.vendor.{ AceEditorSupport, SourceEditor }
import com.thoughtworks.binding.Binding.Var
import com.thoughtworks.binding._
import org.scalajs.dom.Event
import org.scalajs.dom.html.Div

class ShowFlowComponent(id: String)(circuit: FlowCircuit, schedulesComponent: SchedulesComponent) extends HtmlComponent
  with Layout
  with RunLinkComponent
  with SourceEditor {
  val loadedDefinition: Var[Option[FlowDefinitionDetails]] = Var(None)
  val source: Var[Option[String]] = Var(None)

  override def init: Unit = {
    schedulesComponent.init

    circuit.subscribe(circuit.zoom(identity)) { model =>
      loadedDefinition.value = model.value.definition
      source.value = model.value.source
      source.value.foreach(flowSource.setValue(_, -1))
    }

    circuit.dispatch(LoadDefinition(id))
  }

  @dom
  def empty: Binding[Div] = <div></div>

  lazy val flowSource: AceEditorSupport.Editor = sourceEditor("flow-source", mode = "json")

  @dom
  override def element: Binding[Div] =
    <div id="flow" class="row">
      {
        loadedDefinition.bind match {
          case Some(_) =>
            <div>
              <h3>Flow { id } </h3>
              <div class="panel panel-default">
                <div class="panel-heading">
                  <h3 class="panel-title">Source</h3>
                </div>
                <div class="panel-body">
                  <div id="flow-source" style="height: 300px">
                    { source.bind.getOrElse("") }
                  </div>
                </div>
              </div>
              <button class="btn btn-primary" onclick={ (_: Event) => circuit.dispatch(CreateOrUpdate(flowSource.getValue())) }>Save</button>
              { runLink(id).bind }
              { schedulesComponent.element.bind }
            </div>
          case None => <div>flow not found</div>
        }
      }
    </div>

}
