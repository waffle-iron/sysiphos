package com.flowtick.sysiphos.ui
import com.flowtick.sysiphos.flow.FlowDefinitionDetails
import com.flowtick.sysiphos.ui.vendor.ToastrSupport._
import com.flowtick.sysiphos.ui.vendor.{ AceEditorSupport, Toastr }
import com.thoughtworks.binding._
import org.scalajs.dom.Event
import org.scalajs.dom.html.Div

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

class FlowComponent(id: Option[String], sysiphosApi: SysiphosApi) extends HtmlComponent with Layout {
  lazy val sourceEditor: AceEditorSupport.Editor = {
    val editor = AceEditorSupport.edit("flow-source")
    editor.setTheme("ace/theme/textmate")
    editor.session.setMode("ace/mode/json")
    editor
  }

  override def init(): Unit = {
    sourceEditor.setValue("")
  }

  @dom
  def flowOverview(definition: FlowDefinitionDetails): Binding[Div] = {
    sourceEditor.setValue(definition.source.getOrElse(""), pos = 1)
    <div>
      <h3>Flow { definition.id } </h3>
    </div>
  }

  @dom
  def flowSection(id: Option[String]): Binding[Div] =
    <div>
      {
        if (id.isEmpty) {
          empty.bind
        } else FutureBinding(sysiphosApi.getFlowDefinition(id.get)).bind match {
          case Some(Success(Some(definitionResult))) => flowOverview(definitionResult).bind
          case _ => empty.bind
        }
      }
      <div class="panel panel-default">
        <div class="panel-heading">
          <h3 class="panel-title">Source</h3>
        </div>
        <div class="panel-body">
          <div id="flow-source" style="height: 300px"></div>
        </div>
      </div>
      <button class="btn btn-primary" onclick={ (_: Event) => createOrUpdate(sourceEditor.getValue()) }>Save</button>
      <button class="btn btn-default" onclick={ (_: Event) => fillTemplate() }>Template</button>
    </div>

  def fillTemplate(): Unit =
    sourceEditor.setValue(
      s"""
         |{
         |  "id": "new-flow",
         |  "task": {
         |    "id": "new-task",
         |    "type": "shell",
         |    "command": "ls",
         |    "children": [
         |      {
         |        "id": "something",
         |        "type": "noop",
         |        "properties": {
         |          "foo": "bar"
         |        }
         |      }
         |    ]
         |  }
         |}
       """.stripMargin.trim)

  def createOrUpdate(source: String): Unit =
    sysiphosApi
      .createOrUpdateFlowDefinition(source)
      .notifyError
      .successMessage(_ => "Flow updated.")
      .foreach {
        case Some(details) => org.scalajs.dom.window.location.hash = s"#/flow/show/${details.id}"
        case None => Toastr.warning("nothing created")
      }

  @dom
  def empty: Binding[Div] = <div></div>

  @dom
  override val element: Binding[Div] =
    <div>
      { layout(flowSection(id).bind).bind }
    </div>

}
