package com.flowtick.sysiphos.ui.flow

import com.flowtick.sysiphos.flow.{ FlowDefinitionDetails, FlowInstanceContextValue }
import com.flowtick.sysiphos.ui.{ HtmlComponent, Layout }
import com.thoughtworks.binding.Binding.{ Var, Vars }
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.{ Event, HTMLInputElement }

class RunFlowComponent(flowDefinitionId: String)(circuit: FlowCircuit) extends HtmlComponent with Layout {
  val flowDefinition: Var[Option[FlowDefinitionDetails]] = Var(None)
  val contextValues: Vars[FlowInstanceContextValue] = Vars.empty

  val (newKey, newValue) = (Var[String](""), Var[String](""))

  override def init: Unit = {
    circuit.subscribe(circuit.zoom(identity)) { model =>
      flowDefinition.value = model.value.definition
    }

    circuit.dispatch(LoadDefinition(flowDefinitionId))
  }

  @dom
  def element: Binding[Div] = {
    flowDefinition.bind match {
      case Some(definition) =>
        <div id="run-flow" class="row">
          <h3>Run { definition.id }</h3>
          <div class="panel panel-default">
            <div class="panel-heading">
              <h3 class="panel-title">Context Values</h3>
            </div>
            <div class="panel-body">
              <div id="context-table">
                <table class="table table-striped">
                  <thead>
                    <th>Key</th>
                    <th>Value</th>
                    <th>Action</th>
                  </thead>
                  <tbody>
                    {
                      contextValues.map { contextValue =>
                        <tr>
                          <td>{ contextValue.key }</td>
                          <td>{ contextValue.value }</td>
                          <td><button class="btn btn-default" onclick={ _: Event => contextValues.value -= contextValue }>Remove</button></td>
                        </tr>
                      }
                    }
                  </tbody>
                </table>
                <form class="form-inline">
                  <div class="form-group">
                    <label for="keyInput">Key: </label>
                    <input type="text" class="form-control" id="keyInput" placeholder="some key" onchange={ e: Event => newKey.value = e.target.asInstanceOf[HTMLInputElement].value } value={ newKey.bind }/>
                  </div>
                  <div class="form-group">
                    <label for="valueInput">Value: </label>
                    <input type="text" class="form-control" id="valueInput" placeholder="some value" onchange={ e: Event => newValue.value = e.target.asInstanceOf[HTMLInputElement].value } value={ newValue.bind }/>
                  </div>
                  <button type="submit" class="btn btn-default" onclick={ _: Event =>
                    if (newKey.value.nonEmpty && !contextValues.value.exists(_.key == newKey.value)) {
                      contextValues.value += FlowInstanceContextValue(newKey.value, newValue.value)
                      newValue.value = ""
                      newKey.value = ""
                    }
                  }><i class="fa fa-plus"></i></button>
                </form>
              </div>
            </div>
          </div>
          <a class="btn btn-success" onclick={ _: Event => circuit.dispatch(RunInstance(contextValues.value)) }><i class="glyphicon glyphicon-play"></i></a>
        </div>

      case None => <div>flow definition not found</div>
    }
  }

}
