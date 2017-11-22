package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.flow.FlowDefinition.{ SysiphosDefinition, SysiphosTask }
import com.flowtick.sysiphos.task.CommandLineTask
import org.scalatest.{ FlatSpec, Matchers }

class FlowDefinitionSpec extends FlatSpec with Matchers {
  "FlowDefinition" should "be parsed from json" in {
    val tryParse = FlowDefinition.fromJson(
      s"""
         |
         |{
         |  "id": "test-flow",
         |  "task": {
         |    "id": "test-task",
         |    "type": "shell",
         |    "children": [
         |      {
         |        "id": "something",
         |        "type": "noop",
         |        "properties": {
         |          "foo": "bar"
         |        }
         |      }
         |    ],
         |    "command": "ls"
         |  }
         |}
         |
       """.stripMargin.trim)

    val expectedDefinition = SysiphosDefinition(
      id = "test-flow",
      task = CommandLineTask(
        id = "test-task",
        Some(Seq(SysiphosTask(id = "something", `type` = "noop", None, Some(Map("foo" -> "bar"))))),
        command = "ls"))

    tryParse should be(Right(expectedDefinition))
  }
}
