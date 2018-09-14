package com.flowtick.sysiphos.logging
import com.flowtick.sysiphos.logging.Logger.{ LogId, LogStream }
import org.slf4j.LoggerFactory

import scala.util.{ Failure, Success, Try }

class ConsoleLogger extends Logger {
  private val logger = LoggerFactory.getLogger(getClass)

  override def createLog(logKey: String): Try[LogId] = Success("console")

  override def appendToLog(logId: LogId, lines: Seq[String]): Try[Unit] =
    Try(lines.foreach(logger.info))

  override def getLog(logId: LogId): Try[LogStream] =
    Failure(new UnsupportedOperationException("can not get log from console logger"))
}
