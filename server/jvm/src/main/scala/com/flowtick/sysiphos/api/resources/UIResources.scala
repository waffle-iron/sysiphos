package com.flowtick.sysiphos.api.resources

import com.twitter.finagle.http.Status
import io.finch._

trait UIResources extends StaticResourceSupport {
  val indexPageRedirect: Endpoint[Unit] = get(paths[String]) { (_: Seq[String]) =>
    Output.unit(Status.SeeOther).withHeader("Location" -> "index.html")
  }

  val uiResources =
    getResource("index.html", "text/html") :+:
      getResource("sysiphos-ui.js", "text/html") :+:
      indexPageRedirect
}
