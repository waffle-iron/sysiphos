package com.flowtick.sysiphos.execution

import cats.effect.IO
import kamon.Kamon

object Monitoring extends Logging {
  def count(key: String, tags: Map[String, String] = Map.empty): Unit =
    IO(Kamon.counter(key).refine(tags.toList: _*).increment())
      .handleErrorWith(error => IO(log.warn("unable to count", error)))
      .unsafeRunSync()
}
