package com.flowtick.sysiphos.logging
import cats.effect.IO
import com.flowtick.sysiphos.logging.Logger.{ LogId, LogStream }
import fs2.Sink
import org.slf4j.LoggerFactory

class ConsoleLogger extends Logger {
  private val logger = LoggerFactory.getLogger(getClass)

  override def logId(logKey: String): IO[LogId] = IO.pure("console")

  override def getLog(logId: LogId): LogStream =
    fs2.Stream[IO, String]("cant get log from console logger")

  override protected def sink(logId: LogId): Sink[IO, Byte] = in => {
    in.through(fs2.text.utf8Decode).evalMap(line => IO(logger.info(line)))
  }

  override def deleteLog(logId: LogId): IO[Unit] = IO(logger.warn("cant remove any log from the console logger"))
}
