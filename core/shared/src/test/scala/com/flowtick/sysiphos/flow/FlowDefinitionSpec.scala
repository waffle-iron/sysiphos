package com.flowtick.sysiphos.flow

import com.flowtick.sysiphos.flow.FlowDefinition.{ ItemSpec, SysiphosDefinition, SysiphosTask }
import com.flowtick.sysiphos.task._
import org.scalatest.{ FlatSpec, Matchers }

class FlowDefinitionSpec extends FlatSpec with Matchers {
  "FlowDefinition" should "be parsed from json" in {
    val tryParse = FlowDefinition.fromJson(
      s"""
         |
         |{
         |  "id": "test-flow",
         |  "taskRatePerSecond": 3,
         |  "taskParallelism": 5,
         |  "parallelism": 2,
         |  "latestOnly": true,
         |  "tasks": [
         |    {
         |      "id": "test-task",
         |      "type": "shell",
         |      "command": "ls",
         |      "children": [
         |        {
         |          "id": "something",
         |          "type": "noop",
         |          "properties": {
         |            "foo": "bar"
         |          }
         |        },
         |        {
         |          "id": "trigger-task-id",
         |          "type": "trigger",
         |          "flowDefinitionId": "someFlowId"
         |        },
         |        {
         |          "id": "camel-task-id",
         |          "type": "camel",
         |          "uri": "http://example.org",
         |          "bodyTemplate": "Some Request Body"
         |        },
         |        {
         |          "id": "dynamic-task-id",
         |          "type": "dynamic",
         |          "contextSourceUri": "http://example.org/path",
         |          "items": {
         |            "type": "jsonpath",
         |            "expression": "$$.data.items"
         |          }
         |        },
         |        {
         |          "type": "definition-import",
         |          "id": "definition-import-task-id",
         |          "targetDefinitionId": "definition-id",
         |          "fetchTask": {
         |            "id": "fetch-task-id",
         |            "uri": "some uri",
         |            "type": "camel"
         |          },
         |          "items": {
         |            "type": "jsonpath",
         |            "expression": "$$.data.configurations.items"
         |          },
         |          "taskTemplate": {
         |            "id": "id-$${businessKey}",
         |            "uri": "some template uri",
         |            "type": "camel"
         |          }
         |        },
         |        {
         |          "type": "definition-import",
         |          "id": "definition-template-import-task-id",
         |          "definitionTemplate": {
         |            "id": "imported-definition",
         |            "tasks": []
         |          },
         |          "fetchTask": {
         |            "id": "fetch-task-id",
         |            "uri": "some uri",
         |            "type": "camel"
         |          },
         |          "items": {
         |            "type": "jsonpath",
         |            "expression": "$$.data.configurations.items"
         |          },
         |          "taskTemplate": {
         |            "id": "id-$${businessKey}",
         |            "uri": "some template uri",
         |            "type": "camel"
         |          }
         |        }
         |      ]
         |    }
         |  ]
         |}
         |
       """.stripMargin.trim)

    val definitionImportTask = DefinitionImportTask(
      id = "definition-import-task-id",
      fetchTask = CamelTask(
        id = "fetch-task-id",
        uri = "some uri",
        children = None),
      targetDefinitionId = Some("definition-id"),
      items = ItemSpec(`type` = "jsonpath", expression = "$.data.configurations.items"),
      taskTemplate = CamelTask(
        id = "id-${businessKey}",
        uri = "some template uri",
        children = None))

    val expectedDefinition = SysiphosDefinition(
      id = "test-flow",
      taskRatePerSecond = Some(3),
      taskParallelism = Some(5),
      parallelism = Some(2),
      latestOnly = true,
      tasks = Seq(CommandLineTask(
        id = "test-task",
        children = Some(Seq(
          SysiphosTask(id = "something", `type` = "noop", None, Some(Map("foo" -> "bar"))),
          TriggerFlowTask(id = "trigger-task-id", `type` = "trigger", "someFlowId", None),
          CamelTask(id = "camel-task-id", uri = "http://example.org", bodyTemplate = Some("Some Request Body"), children = None),
          DynamicTask(id = "dynamic-task-id", contextSourceUri = "http://example.org/path", children = None, items = ItemSpec(`type` = "jsonpath", expression = "$.data.items")),
          definitionImportTask,
          definitionImportTask.copy(id = "definition-template-import-task-id", targetDefinitionId = None, definitionTemplate = Some(SysiphosDefinition("imported-definition", tasks = Seq.empty))))),
        command = "ls")))

    tryParse should be(Right(expectedDefinition))
  }

  it should "parse execution options" in {
    val tryParse = FlowDefinition.fromJson(
      s"""
         |
         |{
         |  "id": "test-flow",
         |  "latestOnly" : true,
         |  "parallelism": 5,
         |  "tasks": [{
         |    "id": "test-task",
         |    "type": "shell",
         |    "command": "ls",
         |    "children": []
         |  }]
         |}
         |
       """.stripMargin.trim)

    val parsedDefinition = tryParse.right.get
    parsedDefinition.parallelism should be(Some(5))
    parsedDefinition.latestOnly should be(true)
  }

  it should "find a task" in {
    val triggerTask = TriggerFlowTask(id = "trigger-task-id", `type` = "trigger", "someFlowId", children = Some(
      Seq(
        SysiphosTask(id = "child-child", `type` = "noop", None, None))))

    val definition = SysiphosDefinition(
      id = "test-flow",
      tasks = Seq(CommandLineTask(
        id = "test-task",
        command = "ls",
        children = Some(Seq(
          SysiphosTask(id = "something", `type` = "noop", None, None),
          triggerTask)))))

    definition.findTask("trigger-task-id") should be(Some(triggerTask))
    definition.findTask("something") should be(Some(SysiphosTask(id = "something", `type` = "noop", None, None)))
    definition.findTask("child-child") should be(Some(SysiphosTask(id = "child-child", `type` = "noop", None, None)))
    definition.findTask("test-task") should be(definition.tasks.headOption)
    definition.findTask("test") should be(None)
  }

  it should "encode definition" in {
    val json = FlowDefinition.toJson(SysiphosDefinition(
      id = "test-flow",
      tasks = Seq(CommandLineTask(
        id = "test-task",
        Some(Seq(
          SysiphosTask(id = "something", `type` = "noop", None, Some(Map("foo" -> "bar"))),
          TriggerFlowTask(id = "trigger-task-id", `type` = "trigger", "someFlowId", None))),
        command = "ls"))))

    json.contains("trigger-task-id") should be(true)
    json.contains("trigger") should be(true)
    json.contains("something") should be(true)
    json.contains("noop") should be(true)
  }
}
