package com.flowtick.sysiphos.api.resources

import java.util.concurrent.TimeUnit

import com.twitter.finagle.http.{ Response, Status }
import com.twitter.io.{ Buf, Reader }
import com.twitter.util.{ Duration, Future }
import io.finch.{ Endpoint, get }

import scala.util.Try

trait StaticResourceSupport {
  def readerResponse(reader: Try[Reader], contentType: String): Future[Response] = {
    val response = Response()

    reader.fold(error => {
      response.status = Status.NotFound
      response.content = Buf.Utf8(error.getMessage)
      response.contentType = "text/plain"
      Future(response)
    }, contentReader => {
      response.status = Status.Ok
      response.contentType = contentType
      response.charset = "UTF-8"
      response.cacheControl = Duration(1, TimeUnit.DAYS)
      Reader.readAll(contentReader).map(response.content)
    })
  }

  def classPathResource(path: String): Try[Reader] =
    Try(
      Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse(throw new RuntimeException(s"$path not found in classpath"))).map(Reader.fromStream)

  def getResource(
    path: String,
    contentType: String,
    resourcePath: Option[String] = None): Endpoint[Response] =
    get(path)(readerResponse(classPathResource(resourcePath.getOrElse(path)), contentType))
}
