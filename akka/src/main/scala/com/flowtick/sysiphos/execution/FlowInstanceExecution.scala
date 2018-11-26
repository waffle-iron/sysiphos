package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.logging.Logger

import scala.concurrent.{ ExecutionContext, Future }

trait FlowInstanceExecution extends Logging {
  import Logging._

  implicit def executionContext: ExecutionContext

  def flowInstanceRepository: FlowInstanceRepository
  def flowTaskInstanceRepository: FlowTaskInstanceRepository

  protected def isFinished(instancesById: Map[String, Seq[FlowTaskInstance]], task: FlowTask): Boolean =
    instancesById.get(task.id) match {
      case Some(instances) => instances.forall(_.status == FlowTaskInstanceStatus.Done)
      case None => false
    }

  def nextFlowTasks(
    flowDefinition: FlowDefinition,
    taskInstances: Seq[FlowTaskInstance]): Seq[FlowTask] = {

    val instancesById = taskInstances.groupBy(_.taskId)

    val childrenOfDoneParents = Iterator.iterate(flowDefinition.tasks)(
      _.flatMap { task =>
        if (isFinished(instancesById, task))
          task.children.getOrElse(Seq.empty)
        else {
          Seq.empty
        }
      }).takeWhile(_.nonEmpty)

    childrenOfDoneParents.foldLeft(Seq.empty[FlowTask])(_ ++ _).filter(!isFinished(instancesById, _))
  }

  def executeNext(
    flowDefinition: FlowDefinition,
    flowInstance: FlowInstance,
    selectTask: Option[FlowTask],
    currentInstances: Seq[FlowTaskInstance],
    logger: Logger)(implicit repositoryContext: RepositoryContext): Future[Seq[FlowTaskExecution.Execute]] = {
    val nextTasks: Seq[FlowTask] = selectTask.map(Seq(_)).getOrElse(nextFlowTasks(flowDefinition, currentInstances))

    Future.sequence(nextTasks.map { task =>
      for {
        logId <- Future.fromTry(logger.logId(s"${flowInstance.flowDefinitionId}/${flowInstance.id}/${task.id}-${repositoryContext.epochSeconds}"))

        taskInstance <- currentInstances
          .find(_.taskId == task.id)
          .map(Future.successful)
          .getOrElse(flowTaskInstanceRepository.createFlowTaskInstance(flowInstance.id, task.id, logId, taskRetriesDefault(task)))
          .logFailed("unable to find or create task instance")

        _ <- flowTaskInstanceRepository.setStartTime(taskInstance.id, repositoryContext.epochSeconds)

        runningTask <- {
          log.info(s"setting task ${taskInstance.id} to running")
          flowTaskInstanceRepository.setStatus(taskInstance.id, FlowTaskInstanceStatus.Running)
        }

        executedTask <- runningTask match {
          case Some(runningTaskInstance) =>
            val taskLogHeader =
              s"""### running ${task.id} , retries left: ${taskInstance.retries}""".stripMargin

            logger
              .appendLine(runningTaskInstance.logId, taskLogHeader)
              .map(_ => FlowTaskExecution.Execute(task, runningTaskInstance))
              .unsafeToFuture()
          case None => Future.failed(new IllegalArgumentException(s"unable to set running state for task ${task.id}"))
        }
      } yield executedTask
    })
  }

  protected def taskRetriesDefault(task: FlowTask): Int = task match {
    case _ => Configuration.propOrEnv("task.retries.default").map(_.toInt).getOrElse(3)
  }

  protected def retryDelayDefault: Long =
    Configuration.propOrEnv("task.retry.delay.default").map(_.toLong).getOrElse(5 * 60 * 60) // 5 mins in seconds

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