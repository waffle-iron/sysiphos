package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.flow.{ FlowInstanceRepository, FlowTaskInstanceDetails, FlowTaskInstanceRepository, FlowTaskInstanceStatus }
import com.flowtick.sysiphos.task.CommandLineTask
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ FlatSpec, Matchers }

import scala.concurrent.ExecutionContext

class FlowInstanceExecutionSpec extends FlatSpec
  with FlowInstanceExecution
  with Matchers
  with MockFactory {
  "Flow Instance Execution" should "find next tasks" in {
    val cmd4 = CommandLineTask("cmd4", None, "ls 4")

    val secondLevelChildren = Seq(cmd4)
    val cmd3 = CommandLineTask("cmd3", Some(secondLevelChildren), "ls 3")

    val firstLevelChildren = Seq(
      CommandLineTask("cmd2", None, "ls 2"),
      cmd3)

    val root = CommandLineTask("cmd1", Some(firstLevelChildren), "ls 1")

    val flowDefinition = SysiphosDefinition(
      "test", Seq(root))

    val doneTaskInstance = FlowTaskInstanceDetails(
      id = "taskInstanceId",
      flowInstanceId = "flowInstanceId",
      taskId = "cmd1",
      0, None, None, None, 3,
      FlowTaskInstanceStatus.Done, None, None, "log-id")

    nextFlowTasks(flowDefinition, Seq.empty) should be(Seq(root))
    nextFlowTasks(flowDefinition, Seq(doneTaskInstance)) should be(firstLevelChildren)
    nextFlowTasks(flowDefinition, Seq(doneTaskInstance, doneTaskInstance.copy(taskId = "cmd2"))) should be(Seq(cmd3))
    nextFlowTasks(flowDefinition, Seq(doneTaskInstance, doneTaskInstance.copy(taskId = "cmd2"), doneTaskInstance.copy(taskId = "cmd3"))) should be(Seq(cmd4))
  }

  override implicit def executionContext: ExecutionContext = ExecutionContext.Implicits.global

  override def flowInstanceRepository: FlowInstanceRepository = mock[FlowInstanceRepository]

  override def flowTaskInstanceRepository: FlowTaskInstanceRepository = mock[FlowTaskInstanceRepository]
}
