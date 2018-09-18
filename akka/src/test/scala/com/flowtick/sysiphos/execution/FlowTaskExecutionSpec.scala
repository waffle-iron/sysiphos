package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.flow.FlowInstanceContextValue
import org.scalatest.{ FlatSpec, Matchers }

import scala.util.Success

class FlowTaskExecutionSpec extends FlatSpec with FlowTaskExecution with Matchers {
  "Flow execution" should "replace context in template" in {
    System.setProperty("test.property", "testValue")
    replaceContextInTemplate("some context ${foo} : ${bar}, ${test_property}", Seq(
      FlowInstanceContextValue("foo", "fooValue"),
      FlowInstanceContextValue("bar", "barValue")), sanitizedSysProps) should be(Success("some context fooValue : barValue, testValue"))
  }
}
