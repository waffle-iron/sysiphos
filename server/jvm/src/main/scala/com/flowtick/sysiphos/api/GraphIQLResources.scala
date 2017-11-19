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

  private def htmlFile: Reader = Reader.fromStream(getClass.getClassLoader.getResourceAsStream("graphiql.html"))
  private def cssFile: Reader = Reader.fromStream(getClass.getClassLoader.getResourceAsStream("graphiql.min.css"))
  private def jsFile: Reader = Reader.fromStream(getClass.getClassLoader.getResourceAsStream("graphiql.min.js"))

  val graphiql =
    get("graphiql.min.js")(readerResponse(jsFile, "application/javascript")) :+:
      get("graphiql.min.css")(readerResponse(cssFile, "text/css")) :+:
      get("graphiql")(readerResponse(htmlFile, "text/html"))
}
