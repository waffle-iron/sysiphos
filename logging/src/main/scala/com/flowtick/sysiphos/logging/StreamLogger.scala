package com.flowtick.sysiphos.logging

import blobstore.{ Path, Store }
import blobstore.implicits._
import Logger._
import cats.effect.IO
import com.amazonaws.services.s3.model.AmazonS3Exception
import fs2.{ Chunk, Pipe, Sink }

import scala.util.{ Success, Try }

class StreamLogger(
  pathRoot: String,
  store: Store[IO],
  chunkSize: Int = 100) extends Logger {

  private def pathForId(logId: LogId) =
    Path(pathRoot, logId, size = None, isDir = false, None)

  override def logId(logKey: String): Try[LogId] =
    Success(logKey)

  override def getLog(logId: LogId): LogStream =
    store
      .get(pathForId(logId), 4096)
      .through(fs2.text.utf8Decode)
      .through(fs2.text.lines).handleErrorWith {
        case _: java.nio.file.NoSuchFileException => fs2.Stream.empty
        case keyNotFound: AmazonS3Exception if keyNotFound.getErrorCode.equalsIgnoreCase("NoSuchKey") =>
          fs2.Stream.empty
        case other: Throwable => fs2.Stream.raiseError[IO](other)
      }

  override protected def sink(logId: LogId): Sink[IO, Byte] =
    store.bufferedPut(pathForId(logId), logExecutionContext)

  override def pipe: Pipe[IO, String, String] = in => {
    // we chunk up log lines, which will produce bigger strings, to have fewer writes in the sink
    val foldedChunks: fs2.Stream[IO, LogId] =
      in
        .chunkN(chunkSize)
        .fold("")((left, right: Chunk[String]) => left + right.toList.mkString(start = "", sep = "\n", end = "\n"))
        .map(_.trim) // trim applies to chunk start and chunk end only

    super.pipe.apply(foldedChunks)
  }

}