package com.flowtick.sysiphos.api.resources

import com.twitter.finagle.http.Status
import io.finch._

trait UIResources extends StaticResourceSupport {
  val indexPageRedirect: Endpoint[Unit] = get(/) { _: Any =>
    Output.unit(Status.SeeOther).withHeader("Location" -> "index.html")
  }

  val uiResources =
    getResource("index.html", "text/html") :+:
      getResource("sysiphos-ui.js", "text/html") :+:
      getResource("noun_694591_cc.svg", "image/svg+xml") :+:
      indexPageRedirect
}
