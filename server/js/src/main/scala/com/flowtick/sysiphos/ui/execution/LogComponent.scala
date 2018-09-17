package com.flowtick.sysiphos.ui.execution

import com.flowtick.sysiphos.ui.HtmlComponent
import com.flowtick.sysiphos.util.Linkify
import com.thoughtworks.binding.Binding.{ Constants, SingletonBindingSeq, Var }
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
            case Some(logValue) =>
              val logHtml = if (logValue.length > 500000) { // show line numbers and links only for medium sized logs
                SingletonBindingSeq(Binding(<span>{ logValue }</span>))
              } else lineElems(logValue).bind

              <pre>{ logHtml }</pre>
            case None => <span>could not find { logId }</span>
          }
        }
      </div>
    </div>

  @dom
  def lineElems(logValue: String) = {
    def createCodeElem(content: String) = {
      val elem = org.scalajs.dom.window.document.createElement("code")
      elem.innerHTML = Linkify.linkify(content) + "\n"
      elem
    }

    Constants(logValue.split("\n"): _*).map(createCodeElem(_))
  }

}
