package com.flowtick.sysiphos.execution.task

import java.io.File

import com.flowtick.sysiphos.flow.FlowDefinition.ItemSpec
import com.flowtick.sysiphos.logging.ConsoleLogger
import com.flowtick.sysiphos.task.{ CamelTask, DefinitionImportTask }
import org.scalatest.{ FlatSpec, Matchers }

class DefinitionImportTaskExecutionSpec extends FlatSpec with DefinitionImportTaskExecution with Matchers {
  "Definition Import Task" should "read definitions from uri" in {
    val testImportFile = new File(getClass.getClassLoader.getResource("test-import-context.json").getFile)

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
        id = "task-template",
        uri = "uri:${businessKey}", children = None))

    val flowDefinition =
      getFlowDefinition(definitionImportTask, Seq.empty, "test")(new ConsoleLogger).unsafeRunSync()

    flowDefinition.tasks should be(Seq(
      CamelTask(
        id = "task-template",
        uri = "uri:key1", children = None),
      CamelTask(
        id = "task-template",
        uri = "uri:key2", children = None)))
  }
}
