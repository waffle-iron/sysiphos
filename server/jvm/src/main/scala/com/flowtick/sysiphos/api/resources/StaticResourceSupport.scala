package com.flowtick.sysiphos.api.resources

import java.util.concurrent.TimeUnit

import com.twitter.finagle.http.{Response, Status}
import com.twitter.io.Reader
import com.twitter.util.{Duration, Future}
import io.finch.{Endpoint, get}


trait StaticResourceSupport {
  def readerResponse(reader: Reader, contentType: String): Future[Response] = {
    val response = Response()
    response.status = Status.Ok
    response.contentString = contentType
    response.charset = "UTF-8"
    response.cacheControl = Duration(1, TimeUnit.DAYS)
    Reader.readAll(reader).map(response.content)
  }

  def classPathResource(path: String): Reader =
    Reader.fromStream(getClass.getClassLoader.getResourceAsStream(path))

  def getResource(
    path: String,
    contentType: String,
    resourcePath: Option[String] = None): Endpoint[Response] =
    get(path)(readerResponse(classPathResource(resourcePath.getOrElse(path)), contentType))
}
