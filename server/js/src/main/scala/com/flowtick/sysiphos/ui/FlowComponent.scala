package com.flowtick.sysiphos.ui
import com.flowtick.sysiphos.flow.FlowDefinitionDetails
import com.flowtick.sysiphos.ui.vendor.AceEditorSupport
import com.thoughtworks.binding.Binding._
import com.thoughtworks.binding._
import org.scalajs.dom.Event
import org.scalajs.dom.html.Div
import vendor.ToastrSupport._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FlowComponent(id: String, sysiphosApi: SysiphosApi) extends HtmlComponent with Layout {
  val flowDefinition: Vars[FlowDefinitionDetails] = Vars.empty[FlowDefinitionDetails]

  lazy val sourceEditor: AceEditorSupport.Editor = {
    val editor = AceEditorSupport.edit("flow-source")
    editor.setTheme("ace/theme/textmate")
    editor.session.setMode("ace/mode/json")
    editor
  }

  def getDefinition(): Unit =
    sysiphosApi.getFlowDefinition(id).foreach { definitionResult =>
      flowDefinition.value.clear()
      definitionResult.foreach { definition =>
        flowDefinition.value += definition
        sourceEditor.setValue(definition.source.getOrElse(""), 1)
      }
    }

  @dom
  def flowOverview(definition: FlowDefinitionDetails): Binding[Div] = {
    <div></div>
  }

  @dom
  def notFound: Binding[Div] = <div>Flow { id } not found</div>

  @dom
  def flowSection: Binding[Div] =
    <div>
      <h3>Flow { id }</h3>
      <div>
        {
          for (definition <- flowDefinition) yield flowOverview(definition).bind
        }
      </div>
      <div class="panel panel-default">
        <div class="panel-heading">
          <h3 class="panel-title">Source</h3>
        </div>
        <div class="panel-body">
          <div id="flow-source" style="height: 300px"></div>
        </div>
      </div>
      <button class="btn btn-primary" onclick={ (_: Event) => createOrUpdate(sourceEditor.getValue()) }>Save</button>
    </div>

  def createOrUpdate(source: String): Future[Option[FlowDefinitionDetails]] =
    sysiphosApi.createOrUpdateFlowDefinition(source).notifyError

  @dom
  override def element: Binding[Div] = {
    <div>
      { getDefinition(); layout(flowSection.bind).bind }
    </div>
  }

}
