package com.flowtick.sysiphos.execution.task

import com.flowtick.sysiphos.flow.{ FlowTaskInstanceDetails, FlowTaskInstanceStatus }
import com.flowtick.sysiphos.logging.ConsoleLogger
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ FlatSpec, Matchers }

import scala.concurrent.ExecutionContext

class CommandLineTaskExecutionSpec extends FlatSpec with CommandLineTaskExecution with Matchers with MockFactory {
  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  lazy val flowTaskInstance = FlowTaskInstanceDetails(
    id = "task-id",
    flowInstanceId = "instance",
    flowDefinitionId = "definition id",
    taskId = "task",
    creationTime = 1l,
    startTime = None,
    endTime = None,
    retries = 0,
    status = FlowTaskInstanceStatus.New,
    retryDelay = 10,
    nextDueDate = None,
    logId = "log-id")

  "Command line execution" should "run a command and log output" in {
    runCommand(flowTaskInstance, "echo Test", None)(new ConsoleLogger).unsafeRunSync() should be(0)
  }
}
