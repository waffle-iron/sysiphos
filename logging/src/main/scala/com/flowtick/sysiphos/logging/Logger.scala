package com.flowtick.sysiphos.logging

import java.io.{ File, InputStream }

import com.flowtick.sysiphos.config.Configuration.propOrEnv

import scala.util.Try

trait Logger {
  def createLog(logKey: String): Try[Logger.LogId]
  def appendToLog(logId: Logger.LogId, lines: Seq[String]): Try[Unit]
  def getLog(logId: Logger.LogId): Try[Logger.LogStream]
}

object Logger {
  type LogId = String
  type LogStream = InputStream

  def defaultLogger: Logger = propOrEnv("logger.impl", "file").toLowerCase match {
    case "file" =>
      val baseDirPath = propOrEnv("logger.file.baseDir", sys.props.get("java.io.tmpdir").getOrElse("/tmp"))
      new FileLogger(new File(baseDirPath))
    case _ => new ConsoleLogger
  }
}
