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
            <button type="button" class="navbar-toggle collapsed" data:target="#navbar" data:aria-expanded="false" data:aria-controls="navbar">
              <span class="sr-only">Toggle navigation</span>
              <span class="icon-bar"></span>
              <span class="icon-bar"></span>
              <span class="icon-bar"></span>
            </button>
            <a href="#/" class="navbar-brand"><img src="noun_694591_cc.svg" style="width: 1.25em"/></a>
            <span class="navbar-brand" style="left: -0.5em">Sysiphos</span>
          </div>
          <div id="navbar" class="navbar-collapse collapse">
            <ul class="nav navbar-nav">
              <li><a href="#/flows">Flows</a></li>
              <li><a href="#/schedules">Schedules</a></li>
              <li><a target="_blank" href="/graphiql"><i class="fa fa-keyboard" data:aria-hidden="true"></i> API Console</a></li>
            </ul>
          </div><!--/.nav-collapse -->
        </div>
      </nav>
      <div class="container">
        { content }
      </div>
    </div>
}
