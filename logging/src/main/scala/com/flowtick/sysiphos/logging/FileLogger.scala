package com.flowtick.sysiphos.logging

import java.io.{ File, FileInputStream, FileOutputStream }

import cats.effect.IO
import com.flowtick.sysiphos.logging.Logger._
import fs2.{ Pipe, Sink }
import org.slf4j
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

class FileLogger(logBaseDir: File)(executionContext: ExecutionContext) extends Logger {
  val log: slf4j.Logger = LoggerFactory.getLogger(getClass)

  override def logId(logKey: String): Try[LogId] = Try {
    val logFileDir = new File(logBaseDir, s"${logKey.replace('/', File.separatorChar)}")
    logFileDir.mkdirs()

    val logFile = new File(logFileDir, "log.txt")
    logFile.createNewFile()
    logFile
  }.flatMap { logFile =>
    if (logFile.canWrite) {
      log.debug(s"created log $logFile")
      Success(logFile.getAbsolutePath)
    } else
      Failure(new IllegalStateException(s"unable to create logfile for $logKey"))
  }

  override def getLog(logId: LogId): LogStream =
    fs2.io
      .readInputStream[IO](IO(new FileInputStream(logId)), 4096, executionContext)
      .through(fs2.text.utf8Decode)
      .through(fs2.text.lines)

  override protected def sink(logId: LogId): Sink[IO, Byte] =
    fs2.io.writeOutputStream[IO](IO(new FileOutputStream(new File(logId), true)), executionContext)

  override def pipe: Pipe[IO, String, String] = in => super.pipe(in.map(_ + "\n"))
}
