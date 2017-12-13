package com.flowtick.sysiphos.ui

import mhtml.mount
import org.scalajs.dom.window
import pages.DomView
import pages.Page.{Component, page}

import scala.xml.Elem

object SysiphosUI extends App {
  case class DomComponent(element: Elem) extends Component[Elem]

  val domView = new DomView[Elem](element => {
    val appContainer = window.document.getElementById("sysiphos-app")
    appContainer.innerHTML = ""
    mount(appContainer, element)
  })

  page[Elem]("/flows", _ => new FlowsComponent)
    .otherwise(_ => new WelcomeComponent)
    .view(domView)
}
