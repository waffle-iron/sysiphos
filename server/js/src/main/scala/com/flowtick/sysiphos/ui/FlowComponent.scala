package com.flowtick.sysiphos.ui
import com.flowtick.sysiphos.flow.FlowDefinitionDetails
import com.thoughtworks.binding.Binding._
import com.thoughtworks.binding._
import org.scalajs.dom.html.Div

import scala.concurrent.ExecutionContext.Implicits.global

class FlowComponent(id: String, sysiphosApi: SysiphosApi) extends HtmlComponent with Layout {
  val flowDefinition = Vars.empty[FlowDefinitionDetails]

  def getDefinition: Unit =
    sysiphosApi.getFlowDefinition(id).foreach { definitionResult =>
      flowDefinition.value.clear()
      definitionResult.foreach(flowDefinition.value.+=)
    }

  @dom
  def flowOverview(definition: FlowDefinitionDetails): Binding[Div] =
    <div>
      { definition.source.getOrElse("") }
    </div>

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
    </div>

  @dom
  override def element: Binding[Div] = {
    <div>
      { getDefinition; layout(flowSection.bind).bind }
    </div>
  }

}
