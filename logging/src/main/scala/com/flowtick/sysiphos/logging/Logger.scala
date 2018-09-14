package com.flowtick.sysiphos.logging

import java.io.InputStream

import scala.util.Try

trait Logger {
  def createLog(logKey: String): Try[Logger.LogId]
  def appendToLog(logId: Logger.LogId, lines: Seq[String]): Try[Unit]
  def getLog(logId: Logger.LogId): Try[Logger.LogStream]
}

object Logger {
  type LogId = String
  type LogStream = InputStream

  def defaultLogger = new FileLogger
}
