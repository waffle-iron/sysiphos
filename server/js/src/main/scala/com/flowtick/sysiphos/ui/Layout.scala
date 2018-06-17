package com.flowtick.sysiphos.ui

import com.thoughtworks.binding.{ Binding, dom }
import org.scalajs.dom.html.Div

trait Layout {
  @dom
  def layout(content: Div): Binding[Div] =
    <div>
      <nav class="navbar navbar-default navbar-fixed-top">
        <div class="container">
          <div class="navbar-header">
            <button type="button" class="navbar-toggle collapsed" data:toggle="collapse" data:target="#navbar" data:aria-expanded="false" data:aria-controls="navbar">
              <span class="sr-only">Toggle navigation</span>
              <span class="icon-bar"></span>
              <span class="icon-bar"></span>
              <span class="icon-bar"></span>
            </button>
            <a class="navbar-brand"><img src="noun_694591_cc.svg" style="width: 1.25em"/></a>
            <span class="navbar-brand" style="left: -0.5em">Sysiphos</span>
          </div>
          <div id="navbar" class="navbar-collapse collapse">
            <ul class="nav navbar-nav">
              <li class="active"><a href="#/flows">Flows</a></li>
            </ul>
          </div><!--/.nav-collapse -->
        </div>
      </nav>
      <div class="container">
        { content }
      </div>
    </div>
}
