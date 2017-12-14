package com.flowtick.sysiphos.ui

import mhtml.mount
import org.scalajs.dom.window
import pages.DomView
import pages.Page.{ Component, page }

import scala.concurrent.ExecutionContext
import scala.xml.Elem

object SysiphosUI extends App {
  case class DomComponent(element: Elem) extends Component[Elem]

  val domView = new DomView[Elem](element => {
    val appContainer = window.document.getElementById("sysiphos-app")
    appContainer.innerHTML = ""
    mount(appContainer, element)
  })

  val api = new SysiphosApiClient()(ExecutionContext.global)
  val flowsComponent = new FlowsComponent(api)

  /**
   * FIXME: it seems the component is loaded twice (see output of println(ctx)),
   * we reuse it currently, to avoid broken update cycles in the component
   */
  page[Elem]("/flows", ctx => flowsComponent)
    .otherwise(_ => new WelcomeComponent)
    .view(domView)
}
