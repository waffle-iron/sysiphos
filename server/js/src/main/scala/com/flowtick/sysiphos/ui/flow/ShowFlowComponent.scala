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
    <div id="flow">
      <div>
        <h3>Flow { loadedDefinition.bind.map(_.id).getOrElse("") } </h3>
      </div>
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
      {
        loadedDefinition.bind match {
          case Some(flow) => <a href={ s"/graphiql?query=mutation%20%7B%0A%09createInstance(flowDefinitionId%3A%20%22${flow.id}%22%2C%20context%3A%20%5B%0A%20%20%20%20%7Bkey%3A%20%22somekey%22%2C%20value%3A%20%22somevalue%22%7D%2C%0A%20%20%20%20%7Bkey%3A%20%22somekey2%22%2C%20value%3A%20%22somevalue2%22%7D%0A%20%20%5D)%20%7B%0A%20%20%20%20id%2C%20context%20%7Bvalue%7D%0A%20%20%7D%0A%7D" } class="btn btn-success"><i class="glyphicon glyphicon-play"></i></a>
          case None => <!-- -->
        }
      }
      {
        loadedDefinition.bind match {
          case Some(_) => schedulesComponent.element.bind
          case None => <!-- schedules only shown for actual flow -->
        }
      }
    </div>

}
