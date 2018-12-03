package com.flowtick.sysiphos.execution

import kamon.Kamon
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.{ ExecutionContext, Future }

trait Logging {
  val log: Logger = LoggerFactory.getLogger(getClass)
}

object Logging {
  implicit class LogFuture[T](future: Future[T])(implicit executionContext: ExecutionContext) {
    def logFailed(msg: String): Future[T] = future.recoverWith {
      case error =>
        Kamon.counter("future-failed").increment()

        LoggerFactory.getLogger(getClass).error(msg, error)
        Future.failed(error)
    }

    def logSuccess(msg: T => String): Future[T] = {
      future.onComplete(tryValue => tryValue.foreach(value => LoggerFactory.getLogger(getClass).info(msg(value))))
      future
    }
  }
}
