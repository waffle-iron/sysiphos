package com.flowtick.sysiphos

import com.twitter.util.Await

object SysiphosApiServer extends App {
  import io.finch._
  import io.finch.circe._
  import com.twitter.finagle.Http

  val statusEndpoint: Endpoint[String] = get("status") { Ok("OK") }
  val apiEndpoint: Endpoint[String] = get("api") { Ok("api") }

  val service = (statusEndpoint :+: apiEndpoint).toServiceAs[Application.Json]

  Await.ready(Http.server.serve(":" concat sys.props.getOrElse("http.port", 8080).toString, service))
}
