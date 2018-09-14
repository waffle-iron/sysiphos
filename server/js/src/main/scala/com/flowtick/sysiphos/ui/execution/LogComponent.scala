package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.ui.HtmlComponent
import com.thoughtworks.binding.Binding.Var
import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Div

import scala.scalajs.js.URIUtils

class LogComponent(logId: String, circuit: LogCircuit) extends HtmlComponent {
  val log: Var[Option[String]] = Var(None)

  override def init: Unit = {
    circuit.subscribe(circuit.zoom(identity)) { model =>
      log.value = model.value.log
    }

    circuit.dispatch(LoadLog(URIUtils.decodeURIComponent(logId)))
  }

  @dom
  override def element: Binding[Div] =
    <div class="row">
      <div class="col-lg-12">
        {
          log.bind match {
            case Some(logValue) => <pre>{ logValue }</pre>
            case None => <span>could not find { logId }</span>
          }
        }
      </div>
    </div>
}
