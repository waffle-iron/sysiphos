package com.flowtick.sysiphos.execution

import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.{ ExecutionContext, Future }

trait Logging {
  val log: Logger = LoggerFactory.getLogger(getClass)
}

object Logging {
  implicit class LogFuture[T](future: Future[T])(implicit executionContext: ExecutionContext) {
    def logFailed(msg: String): Future[T] = future.recoverWith {
      case error =>
        LoggerFactory.getLogger(getClass).error(msg, error)
        Future.failed(error)
    }
  }
}
