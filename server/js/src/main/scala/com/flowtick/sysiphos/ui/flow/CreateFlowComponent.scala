package com.flowtick.sysiphos.ui.flow

import com.flowtick.sysiphos.flow.FlowDefinition
import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.task.CommandLineTask
import com.flowtick.sysiphos.ui.vendor.{ AceEditorSupport, SourceEditor }
import com.flowtick.sysiphos.ui.{ HtmlComponent, Layout }
import com.thoughtworks.binding.Binding.Var
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.Event
import org.scalajs.dom.html.Div

case class CreateFlowModel()

class CreateFlowComponent(circuit: FlowCircuit) extends HtmlComponent with Layout with SourceEditor {
  val source: Var[Option[String]] = Var(None)

  override def init: Unit = {
    circuit.subscribe(circuit.zoom(identity)) { model =>
      source.value = model.value.source
      source.value.foreach(flowSource.setValue(_, 0))
    }

    circuit.dispatch(ResetSource)
  }

  lazy val flowSource: AceEditorSupport.Editor = sourceEditor("flow-source", mode = "json")

  def fillTemplate(): Unit =
    flowSource.setValue(
      FlowDefinition.toJson(SysiphosDefinition(
        "new-flow", tasks = Seq(
          CommandLineTask(
            id = "shell-task",
            children = None,
            shell = Some("bash"),
            command = "echo 'Hello World!'")))), 0)

  @dom
  override def element: Binding[Div] =
    <div id="flow">
      <div>
        <h3>New Flow</h3>
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
      <button class="btn btn-default" onclick={ (_: Event) => fillTemplate() }>Template</button>
    </div>

}
