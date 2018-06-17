package com.flowtick.sysiphos.ui

import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Div
import org.scalajs.dom.window
import pages.DomView
import pages.Page.{ Component, page }

import scala.concurrent.ExecutionContext

object SysiphosUI extends App {
  case class DomComponent(element: Binding[Div]) extends Component[Binding[Div]]

  val domView = new DomView[Binding[Div]](element => {
    element.unwatch()
    val appContainer = window.document.getElementById("sysiphos-app")
    appContainer.innerHTML = ""
    dom.render(appContainer, element)
  })

  val api = new SysiphosApiClient()(ExecutionContext.global)
  val flowsComponent = new FlowsComponent(api)

  /**
   * FIXME: it seems the component is loaded twice (see output of println(ctx)),
   * we reuse it currently, to avoid broken update cycles in the component
   */
  page[Binding[Div]]("/flows", ctx => flowsComponent)
    .otherwise(_ => new WelcomeComponent)
    .view(domView)
}
