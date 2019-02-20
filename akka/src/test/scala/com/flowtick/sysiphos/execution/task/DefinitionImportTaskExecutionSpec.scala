package com.flowtick.sysiphos.execution.task

import java.io.File

import com.flowtick.sysiphos.flow.FlowDefinition.ItemSpec
import com.flowtick.sysiphos.flow.FlowInstanceContextValue
import com.flowtick.sysiphos.logging.ConsoleLogger
import com.flowtick.sysiphos.task.{CamelTask, DefinitionImportTask, TriggerFlowTask}
import org.scalatest.{FlatSpec, Matchers}

class DefinitionImportTaskExecutionSpec extends FlatSpec with DefinitionImportTaskExecution with Matchers {
  val testImportFile = new File(getClass.getClassLoader.getResource("test-import-context.json").getFile)

  "Definition Import Task" should "read definitions from uri" in {
    val definitionImportTask = DefinitionImportTask(
      id = "import-task",
      fetchTask = CamelTask(
        id = "camel-fetch-task",
        uri = s"file:${testImportFile.getParent}?fileName=${testImportFile.getName}&noop=true",
        exchangeType = Some("consumer"),
        children = None),
      targetDefinitionId = "imported-definition",
      items = ItemSpec(
        `type` = "jsonpath",
        expression = "$.data"),
      taskTemplate = CamelTask(
        id = "task-${businessKey}",
        uri = "uri:${businessKey}", children = None))

    val flowDefinition =
      getFlowDefinition(definitionImportTask, Seq.empty, "test")(new ConsoleLogger).unsafeRunSync()

    flowDefinition.tasks should be(Seq(
      CamelTask(
        id = "task-key1",
        uri = "uri:key1", children = None),
      CamelTask(
        id = "task-key2",
        uri = "uri:key2", children = None)))
  }

  it should "support trigger task context replacement" in {
    val definitionImportTask = DefinitionImportTask(
      id = "import-task",
      fetchTask = CamelTask(
        id = "camel-fetch-task",
        uri = s"file:${testImportFile.getParent}?fileName=${testImportFile.getName}&noop=true",
        exchangeType = Some("consumer"),
        children = None),
      targetDefinitionId = "trigger-imported-definition",
      items = ItemSpec(
        `type` = "jsonpath",
        expression = "$.data"),
      taskTemplate = TriggerFlowTask(
        "trigger-${businessKey}",
        flowDefinitionId = "some-flow",
        children = None,
        context = Some(Seq(
        FlowInstanceContextValue("foo", "${businessKey}"),
      ))))

    val flowDefinition =
      getFlowDefinition(definitionImportTask, Seq.empty, "test")(new ConsoleLogger).unsafeRunSync()

    flowDefinition.tasks should be(Seq(
      TriggerFlowTask(
        "trigger-key1",
        flowDefinitionId = "some-flow",
        children = None,
        context = Some(Seq(
          FlowInstanceContextValue("foo", "key1"),
        ))),
      TriggerFlowTask(
        "trigger-key2",
        flowDefinitionId = "some-flow",
        children = None,
        context = Some(Seq(
          FlowInstanceContextValue("foo", "key2"),
        )))))
  }
}
