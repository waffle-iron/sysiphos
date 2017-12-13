package com.flowtick.sysiphos

import pages.Page.Component

import scala.xml.Elem

package object ui {
  trait HtmlComponent extends Component[Elem]
}
