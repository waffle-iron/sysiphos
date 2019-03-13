package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.ui.HtmlComponent
import com.thoughtworks.binding.{ Binding, dom }
import com.thoughtworks.binding.Binding.Var
import org.scalajs.dom.html.Div

class VersionComponent(circuit: VersionCircuit) extends HtmlComponent {
  val version: Var[Option[String]] = Var(None)

  override def init: Unit = {
    circuit.subscribe(circuit.zoom(identity)) { model =>
      version.value = model.value.version
    }

    circuit.dispatch(GetVersion)
  }

  @dom
  override def element: Binding[Div] =
    <div class="row">
      <div class="col-lg-12">
        {
          version.bind match {
            case Some(v) =>
              <pre>
                <a href={ "https://github.com/flowtick/sysiphos/commit/" + v }> version: { v } </a>
              </pre>
            case None => <span>version not available</span>
          }
        }
      </div>
    </div>
}
