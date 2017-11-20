package com.flowtick.sysiphos.api.resources

trait UIResources extends StaticResourceSupport {
  val uiResources =
    getResource("index.html", "text/html") :+:
      getResource("sysiphos-ui.js", "text/html")
}
