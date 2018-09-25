package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.flow.FlowDefinition.{ SysiphosDefinition, SysiphosTask }
import com.flowtick.sysiphos.task.{ CommandLineTask, TriggerFlowTask }
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
         |    "command": "ls",
         |    "children": [
         |      {
         |        "id": "something",
         |        "type": "noop",
         |        "properties": {
         |          "foo": "bar"
         |        }
         |      },
         |      {
         |        "id": "trigger-task-id",
         |        "type": "trigger",
         |        "flowDefinitionId": "someFlowId"
         |      }
         |    ]
         |  }
         |}
         |
       """.stripMargin.trim)

    val expectedDefinition = SysiphosDefinition(
      id = "test-flow",
      task = CommandLineTask(
        id = "test-task",
        Some(Seq(
          SysiphosTask(id = "something", `type` = "noop", None, Some(Map("foo" -> "bar"))),
          TriggerFlowTask(id = "trigger-task-id", `type` = "trigger", "someFlowId", None))),
        command = "ls"))

    tryParse should be(Right(expectedDefinition))
  }

  it should "find a task" in {
    val triggerTask = TriggerFlowTask(id = "trigger-task-id", `type` = "trigger", "someFlowId", children = Some(
      Seq(
        SysiphosTask(id = "child-child", `type` = "noop", None, None))))

    val definition = SysiphosDefinition(
      id = "test-flow",
      task = CommandLineTask(
        id = "test-task",
        command = "ls",
        children = Some(Seq(
          SysiphosTask(id = "something", `type` = "noop", None, None),
          triggerTask))))

    definition.findTask("trigger-task-id") should be(Some(triggerTask))
    definition.findTask("something") should be(Some(SysiphosTask(id = "something", `type` = "noop", None, None)))
    definition.findTask("child-child") should be(Some(SysiphosTask(id = "child-child", `type` = "noop", None, None)))
    definition.findTask("test-task") should be(Some(definition.task))
    definition.findTask("test") should be(None)
  }

  it should "encode definition" in {
    val json = FlowDefinition.toJson(SysiphosDefinition(
      id = "test-flow",
      task = CommandLineTask(
        id = "test-task",
        Some(Seq(
          SysiphosTask(id = "something", `type` = "noop", None, Some(Map("foo" -> "bar"))),
          TriggerFlowTask(id = "trigger-task-id", `type` = "trigger", "someFlowId", None))),
        command = "ls")))

    json.contains("trigger-task-id") should be(true)
    json.contains("trigger") should be(true)
    json.contains("something") should be(true)
    json.contains("noop") should be(true)
  }
}
