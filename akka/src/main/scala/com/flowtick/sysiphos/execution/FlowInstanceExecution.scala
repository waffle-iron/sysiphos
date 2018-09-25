package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.flow._

trait FlowInstanceExecution extends Logging {
  protected def isFinished(instancesById: Map[String, Seq[FlowTaskInstance]], task: FlowTask): Boolean =
    instancesById.get(task.id) match {
      case Some(instances) => instances.forall(_.status == FlowTaskInstanceStatus.Done)
      case None => false
    }

  def nextFlowTasks(
    flowDefinition: FlowDefinition,
    taskInstances: Seq[FlowTaskInstance]): Seq[FlowTask] = {

    val instancesById = taskInstances.groupBy(_.taskId)

    val childrenOfDoneParents = Iterator.iterate(Seq(flowDefinition.task))(
      _.flatMap { task =>
        if (isFinished(instancesById, task))
          task.children.getOrElse(Seq.empty)
        else {
          Seq.empty
        }
      }).takeWhile(_.nonEmpty)

    childrenOfDoneParents.foldLeft(Seq.empty[FlowTask])(_ ++ _).filter(!isFinished(instancesById, _))
  }

}

object FlowInstanceExecution {
  sealed trait FlowInstanceMessage

  case class Execute(taskId: Option[String]) extends FlowInstanceMessage

  case class WorkTriggered(tasks: Seq[FlowTaskExecution.Execute]) extends FlowInstanceMessage
  case class WorkDone(flowTaskInstance: FlowTaskInstance) extends FlowInstanceMessage
  case class WorkFailed(e: Throwable, flowTaskInstance: FlowTaskInstance) extends FlowInstanceMessage
  case class RetryScheduled(flowTaskInstance: FlowTaskInstance) extends FlowInstanceMessage

  case class ExecutionFailed(flowInstance: FlowInstance) extends FlowInstanceMessage
  case class Finished(flowInstance: FlowInstance) extends FlowInstanceMessage
}