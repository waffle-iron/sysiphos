package com.flowtick.sysiphos.execution

import org.slf4j.{ Logger, LoggerFactory }

trait Logging {
  val log: Logger = LoggerFactory.getLogger(getClass)
}
