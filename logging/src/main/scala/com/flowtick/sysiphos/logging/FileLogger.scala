package com.flowtick.sysiphos.logging

import java.io.{ File, FileInputStream, FileOutputStream }

import scala.util.{ Failure, Success, Try }
import Logger._
import org.slf4j
import org.slf4j.LoggerFactory

class FileLogger extends Logger {
  val log: slf4j.Logger = LoggerFactory.getLogger(getClass)

  val logBaseDir = new File("/tmp")

  override def createLog(logKey: String): Try[LogId] = Try {
    val logFile = new File(logBaseDir, s"log-$logKey.log")
    logFile.createNewFile()
    logFile
  }.flatMap { logFile =>
    if (logFile.canWrite) {
      log.debug(s"created log $logFile")
      Success(logFile.getAbsolutePath)
    } else
      Failure(new IllegalStateException(s"unable to create logfile for $logKey"))
  }

  override def appendToLog(logId: LogId, lines: Seq[String]): Try[Unit] = Try {
    val output = new FileOutputStream(new File(logId), true)
    lines.foreach(line => output.write((line + "\n").getBytes("UTF-8")))
    output.flush()
    output.close()
  }

  override def getLog(logId: LogId): Try[LogStream] = Try(new FileInputStream(logId))
}
