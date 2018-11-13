package com.flowtick.sysiphos.execution

import akka.actor.{ Actor, ActorRef, Props }
import akka.pattern.pipe
import com.flowtick.sysiphos.config.Configuration
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowInstanceExecution._
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.logging.Logger
import Logging._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FlowInstanceExecutorActor(
  flowDefinition: FlowDefinition,
  flowInstance: FlowInstance,
  flowInstanceRepository: FlowInstanceRepository,
  flowTaskInstanceRepository: FlowTaskInstanceRepository,
  logger: Logger)(implicit repositoryContext: RepositoryContext)
  extends Actor with FlowInstanceExecution {

  def executeNext(selectTask: Option[FlowTask]): Future[Seq[FlowTaskExecution.Execute]] = flowTaskInstanceRepository
    .getFlowTaskInstances(Some(flowInstance.id), None, None)
    .flatMap { currentInstances =>
      val nextTasks: Seq[FlowTask] = selectTask.map(Seq(_)).getOrElse(nextFlowTasks(flowDefinition, currentInstances))
      Future.sequence(nextTasks.map { task =>

        val taskInstanceFuture = currentInstances
          .find(_.taskId == task.id)
          .map(Future.successful)
          .getOrElse(flowTaskInstanceRepository.createFlowTaskInstance(flowInstance.id, task.id, taskRetriesDefault(task)))
          .logFailed("unable to find or create task instance")

        val taskInstance: Future[Option[FlowTaskInstanceDetails]] = for {
          taskInstance <- taskInstanceFuture
          _ <- flowTaskInstanceRepository.setStartTime(taskInstance.id, repositoryContext.epochSeconds)
          running <- {
            log.info(s"setting task ${taskInstance.id} to running")
            flowTaskInstanceRepository.setStatus(taskInstance.id, FlowTaskInstanceStatus.Running)
          }
        } yield running

        executeRunning(task, taskInstance)
      })
    }

  private def taskRetriesDefault(task: FlowTask) = task match {
    case _ => Configuration.propOrEnv("task.retries.default").map(_.toInt).getOrElse(3)
  }

  private def retryDelayDefault: Long =
    Configuration.propOrEnv("task.retry.delay.default").map(_.toLong).getOrElse(5 * 60 * 60) // 5 mins in seconds

  private def executeRunning(
    task: FlowTask,
    runningInstance: Future[Option[FlowTaskInstanceDetails]]): Future[FlowTaskExecution.Execute] = {
    runningInstance.flatMap {
      case Some(taskInstance) =>
        val log = logger.logId(s"${flowInstance.flowDefinitionId}/${taskInstance.taskId}-${taskInstance.id}")

        val executeWithLogId: Future[FlowTaskExecution.Execute] = for {
          logId <- Future.fromTry(log)
          _ <- flowTaskInstanceRepository.setLogId(taskInstance.id, logId)
        } yield FlowTaskExecution.Execute(task, logId)

        executeWithLogId.pipeTo(flowTaskExecutor(taskInstance))

      case None => Future.failed(new IllegalStateException("unable to set instance to running"))
    }
  }

  def flowTaskExecutor(taskInstance: FlowTaskInstance): ActorRef =
    context.actorOf(Props(new FlowTaskExecutionActor(taskInstance, flowInstance, context.parent, logger)))

  def selfRef: ActorRef = self

  override def receive: PartialFunction[Any, Unit] = {
    case FlowInstanceExecution.Execute(optionalTask) =>
      log.info(s"executing $flowDefinition, $flowInstance ...")
      executeNext(selectTask = optionalTask.flatMap(flowDefinition.findTask)).flatMap {
        case Nil => instanceStatus
        case newExecutions => Future.successful(WorkTriggered(newExecutions))
      }.pipeTo(context.parent)

    case FlowInstanceExecution.WorkFailed(error, flowTaskInstance) =>
      log.warn(s"task ${flowTaskInstance.id} failed with ${error.getLocalizedMessage}")
      context.stop(sender())

      val handledFailure = for {
        _ <- flowTaskInstanceRepository.setEndTime(flowTaskInstance.id, repositoryContext.epochSeconds)
        handled <- handleFailedTask(flowTaskInstance, error)
      } yield handled

      handledFailure.pipeTo(context.parent)

    case FlowInstanceExecution.WorkDone(flowTaskInstance) =>
      context.stop(sender())
      log.info(s"Work is done for task with id ${flowTaskInstance.taskId}.")

      val doneTaskInstance = for {
        _ <- flowTaskInstanceRepository.setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Done)
        ended <- flowTaskInstanceRepository.setEndTime(flowTaskInstance.id, repositoryContext.epochSeconds)
      } yield ended

      doneTaskInstance.map(_ => {
        FlowInstanceExecution.Execute(None)
      }).pipeTo(selfRef)
  }

  private def instanceStatus: Future[FlowInstanceExecution.FlowInstanceMessage] =
    for {
      taskInstances <- flowTaskInstanceRepository.getFlowTaskInstances(Some(flowInstance.id), None, None)
    } yield {
      if (taskInstances.forall(_.status == FlowTaskInstanceStatus.Done))
        Finished(flowInstance)
      else
        ExecutionFailed(flowInstance)
    }

  private def handleFailedTask(flowTaskInstance: FlowTaskInstance, error: Throwable): Future[FlowInstanceMessage] =
    if (flowTaskInstance.retries == 0) {
      log.error(s"retries exceeded for $flowTaskInstance, failed execution", error)
      flowTaskInstanceRepository
        .setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Failed)
        .map(_ => FlowInstanceExecution.ExecutionFailed(flowInstance))
    } else {
      val dueDate = repositoryContext.epochSeconds + flowTaskInstance.retryDelay.getOrElse(retryDelayDefault)
      log.info(s"scheduling retry for ${flowTaskInstance.id} for $dueDate")

      for {
        _ <- flowTaskInstanceRepository.setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Retry)
        _ <- flowTaskInstanceRepository.setRetries(flowTaskInstance.id, flowTaskInstance.retries - 1)
        _ <- flowTaskInstanceRepository.setNextDueDate(flowTaskInstance.id, Some(dueDate))
      } yield RetryScheduled(flowTaskInstance)
    }

}

