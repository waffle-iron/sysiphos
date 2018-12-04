package com.flowtick.sysiphos.execution

import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.core.{ Clock, RepositoryContext }
import com.flowtick.sysiphos.flow.{ FlowTaskInstanceStatus, _ }
import com.flowtick.sysiphos.logging.Logger

import scala.concurrent.{ ExecutionContext, Future }

trait FlowInstanceExecution extends Logging with Clock {
  implicit def executionContext: ExecutionContext

  def flowTaskInstanceRepository: FlowTaskInstanceRepository

  protected def taskInstanceFilter[T](
    instancesById: Map[String, Seq[FlowTaskInstance]],
    task: FlowTask)(predicate: FlowTaskInstance => Boolean): Boolean =
    instancesById.get(task.id) match {
      case Some(instances) => instances.forall(predicate)
      case None => false
    }

  def isPending(taskInstance: FlowTaskInstance): Boolean =
    isRunnable(taskInstance) || taskInstance.status == FlowTaskInstanceStatus.Running

  def isRunnable(taskInstance: FlowTaskInstance): Boolean = taskInstance.status match {
    case FlowTaskInstanceStatus.New => true
    case FlowTaskInstanceStatus.Retry => true
    case _ => false
  }

  def nextFlowTasks(
    taskSelection: FlowTaskSelection,
    flowDefinition: FlowDefinition,
    taskInstances: Seq[FlowTaskInstance]): Seq[FlowTask] = {
    lazy val instancesById: Map[String, Seq[FlowTaskInstance]] = taskInstances.groupBy(_.taskId)

    (taskSelection match {
      case TaskId(id) => flowDefinition.findTask(id).map(Seq(_))
      case PendingTasks => Some(flowDefinition.tasks).map(findChildrenOfDoneTasks(_, instancesById))
    }).getOrElse(Seq.empty)
  }

  def findChildrenOfDoneTasks(searchTasks: Seq[FlowTask], instancesById: => Map[String, Seq[FlowTaskInstance]]): Seq[FlowTask] = {
    val childrenOfDoneParents = Iterator.iterate(searchTasks)(
      _.flatMap { task =>
        if (taskInstanceFilter(instancesById, task)(_.status == FlowTaskInstanceStatus.Done))
          task.children.getOrElse(Seq.empty)
        else {
          Seq.empty
        }
      }).takeWhile(_.nonEmpty)

    childrenOfDoneParents
      .foldLeft(Seq.empty[FlowTask])(_ ++ _)
      .filterNot(taskInstanceFilter(instancesById, _)(_.status == FlowTaskInstanceStatus.Done))
      .filterNot(taskInstanceFilter(instancesById, _)(isPending))
  }

  def getOrCreateTaskInstance(
    flowInstance: FlowInstance,
    task: FlowTask,
    logger: Logger)(implicit repositoryContext: RepositoryContext): Future[FlowTaskInstanceDetails] = {
    def createInstance: Future[FlowTaskInstanceDetails] =
      Future
        .fromTry(logger.logId(s"${flowInstance.flowDefinitionId}/${flowInstance.id}/${task.id}-${repositoryContext.epochSeconds}"))
        .flatMap(logId => {
          val (initialStatus: Option[FlowTaskInstanceStatus.FlowTaskInstanceStatus], dueDate: Option[Long]) = task.startDelay.map { delay =>
            (Some(FlowTaskInstanceStatus.Retry), Some(repositoryContext.epochSeconds + delay))
          }.getOrElse((None, None))

          flowTaskInstanceRepository.createFlowTaskInstance(
            flowInstanceId = flowInstance.id, flowTaskId = task.id, logId = logId,
            retries = task.retries.getOrElse(taskRetriesDefault(task)),
            retryDelay = task.retryDelay.getOrElse(retryDelayDefault),
            dueDate = dueDate,
            initialStatus = initialStatus)
        })

    for {
      foundInstance <- flowTaskInstanceRepository.findOne(FlowTaskInstanceQuery(flowInstanceId = Some(flowInstance.id), taskId = Some(task.id)))
      taskInstance <- foundInstance match {
        case Some(existingInstance) => Future.successful(existingInstance)
        case None => createInstance
      }
    } yield taskInstance
  }

  def setRunning(
    execute: FlowTaskExecution.Execute,
    logger: Logger)(implicit repositoryContext: RepositoryContext): Future[FlowTaskExecution.Execute] = for {
    _ <- flowTaskInstanceRepository.setStartTime(execute.taskInstance.id, repositoryContext.epochSeconds)

    runningTask <- if (execute.taskInstance.status != FlowTaskInstanceStatus.Running) {
      log.info(s"setting task ${execute.taskInstance.id} to running")
      flowTaskInstanceRepository.setStatus(execute.taskInstance.id, FlowTaskInstanceStatus.Running)
    } else Future.successful(Some(execute.taskInstance))

    preparedTask <- runningTask match {
      case Some(runningTaskInstance) =>
        val taskLogHeader =
          s"""### running ${execute.flowTask.id} , retries left: ${execute.taskInstance.retries}""".stripMargin

        logger
          .appendLine(runningTaskInstance.logId, taskLogHeader)
          .map(_ => execute)
          .unsafeToFuture()
      case None => Future.failed(new IllegalArgumentException(s"unable to set running state for task ${execute.flowTask.id}"))
    }
  } yield preparedTask

  protected def taskRetriesDefault(task: FlowTask): Int = task match {
    case _ => Configuration.propOrEnv("task.retries.default").map(_.toInt).getOrElse(3)
  }

  protected def retryDelayDefault: Long =
    Configuration.propOrEnv("task.retry.delay.default").map(_.toLong).getOrElse(5 * 60) // 5 mins in seconds

}

object FlowInstanceExecution {
  sealed trait FlowInstanceMessage

  case class Execute(taskSelection: FlowTaskSelection) extends FlowInstanceMessage

  case class WorkDone(flowTaskInstance: FlowTaskInstance, addToContext: Seq[FlowInstanceContextValue] = Seq.empty) extends FlowInstanceMessage
  case class TaskCompleted(flowTaskInstance: FlowTaskInstance) extends FlowInstanceMessage
  case class WorkFailed(e: Throwable, flowTaskInstance: FlowTaskInstance) extends FlowInstanceMessage
  case class RetryScheduled(flowTaskInstance: FlowTaskInstance) extends FlowInstanceMessage

  case class WorkPending(flowInstance: FlowInstance) extends FlowInstanceMessage
  case class ExecutionFailed(flowInstance: FlowInstance) extends FlowInstanceMessage
  case class Finished(flowInstance: FlowInstance) extends FlowInstanceMessage
}