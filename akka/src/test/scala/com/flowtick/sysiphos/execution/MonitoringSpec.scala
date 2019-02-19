package com.flowtick.sysiphos.execution

import org.scalatest.FlatSpec

class MonitoringSpec extends FlatSpec {
  "Monitoring" should "count without error" in {
    Monitoring.count("test")
  }
}
