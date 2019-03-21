package com.flowtick.sysiphos.logging

import java.util.NoSuchElementException

import blobstore.{ Path, Store }
import blobstore.implicits._
import Logger._
import cats.effect.IO
import com.amazonaws.services.s3.model.AmazonS3Exception
import fs2.{ Chunk, Pipe, Sink }

class StreamLogger(
  pathRoot: String,
  store: Store[IO],
  chunkSize: Int = 100) extends Logger {

  protected val storeChunkSize: Int = 4096

  private def pathForId(logId: LogId) =
    Path(pathRoot, logId, size = None, isDir = false, None)

  override def logId(logKey: String): IO[LogId] =
    IO.pure(logKey)

  override def getLog(logId: LogId): LogStream =
    store
      .get(pathForId(logId), storeChunkSize)
      .through(fs2.text.utf8Decode)
      .through(fs2.text.lines)
      .handleErrorWith {
        case fileNotFound: java.nio.file.NoSuchFileException => fs2.Stream.raiseError[IO](new NoSuchElementException(s"log file $logId could not be found: $fileNotFound"))
        case keyNotFound: AmazonS3Exception if keyNotFound.getErrorCode.equalsIgnoreCase("NoSuchKey") =>
          fs2.Stream.raiseError[IO](new NoSuchElementException(s"log key $logId could not be found: $keyNotFound"))
        case other: Throwable => fs2.Stream.raiseError[IO](other)
      }

  override protected def sink(logId: LogId): Sink[IO, Byte] =
    store.bufferedPut(pathForId(logId), logExecutionContext)

  override protected def source(logId: LogId): fs2.Stream[IO, String] = getLog(logId).handleErrorWith {
    case _: NoSuchElementException => fs2.Stream.empty
    case other => fs2.Stream.raiseError[IO](other)
  }

  override def pipe: Pipe[IO, String, String] = in => {
    // we chunk up log lines, which will produce bigger strings, to have fewer writes in the sink
    val foldedChunks: fs2.Stream[IO, LogId] =
      in
        .chunkN(chunkSize)
        .fold("")((left, right: Chunk[String]) => left + right.toList.mkString(start = "", sep = "\n", end = "\n"))
        .map(_.trim) // trim applies to chunk start and chunk end only

    super.pipe.apply(foldedChunks)
  }

  override def deleteLog(logId: LogId): IO[Unit] = store.remove(pathForId(logId))
}