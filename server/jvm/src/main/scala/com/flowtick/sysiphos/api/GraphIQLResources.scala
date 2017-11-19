package com.flowtick.sysiphos.api

import com.twitter.finagle.http.{ Response, Status }
import com.twitter.io.Reader
import com.twitter.util.Future
import io.finch._

trait GraphIQLResources {

  private def readerResponse(reader: Reader, contentType: String): Future[Response] = {
    val response = Response()
    response.status = Status.Ok
    response.contentString = contentType
    response.charset = "UTF-8"
    Reader.readAll(reader).map(response.content)
  }

  private def classPathResource(path: String): Reader =
    Reader.fromStream(getClass.getClassLoader.getResourceAsStream(path))

  private def getResource(resourcePath: String, contentType: String) =
    get(resourcePath)(readerResponse(classPathResource(resourcePath), contentType))

  val graphiql =
    getResource("es6-promise.auto.min.js", "application/javascript") :+:
    getResource("fetch.min.js", "application/javascript") :+:
    getResource("react.min.js", "application/javascript") :+:
    getResource("react-dom.min.js", "application/javascript") :+:
    getResource("graphiql.min.js", "application/javascript") :+:
    getResource("graphiql.min.css", "text/css") :+:
    get("graphiql")(readerResponse(classPathResource("graphiql.html"), "text/html"))
}
