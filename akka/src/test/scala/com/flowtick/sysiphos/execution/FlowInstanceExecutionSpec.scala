package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.flow.{ FlowTaskInstanceDetails, FlowTaskInstanceStatus }
import com.flowtick.sysiphos.task.CommandLineTask
import org.scalatest.{ FlatSpec, Matchers }

class FlowInstanceExecutionSpec extends FlatSpec with FlowInstanceExecution with Matchers {
  "Flow Instance Execution" should "find next tasks" in new FlowInstanceExecution {
    val cmd4 = CommandLineTask("cmd4", None, "ls 4")

    val secondLevelChildren = Seq(cmd4)
    val cmd3 = CommandLineTask("cmd3", Some(secondLevelChildren), "ls 3")

    val firstLevelChildren = Seq(
      CommandLineTask("cmd2", None, "ls 2"),
      cmd3)

    val root = CommandLineTask("cmd1", Some(firstLevelChildren), "ls 1")

    val flowDefinition = SysiphosDefinition(
      "test", root)

    val doneTaskInstance = FlowTaskInstanceDetails(
      id = "taskInstanceId",
      flowInstanceId = "flowInstanceId",
      taskId = "cmd1",
      0, None, None, None, 3,
      FlowTaskInstanceStatus.Done, None, None)

    nextFlowTasks(flowDefinition, Seq.empty) should be(Seq(root))
    nextFlowTasks(flowDefinition, Seq(doneTaskInstance)) should be(firstLevelChildren)
    nextFlowTasks(flowDefinition, Seq(doneTaskInstance, doneTaskInstance.copy(taskId = "cmd2"))) should be(Seq(cmd3))
    nextFlowTasks(flowDefinition, Seq(doneTaskInstance, doneTaskInstance.copy(taskId = "cmd2"), doneTaskInstance.copy(taskId = "cmd3"))) should be(Seq(cmd4))
  }
}
