package com.flowtick.sysiphos.execution

import java.util.concurrent.Executors

import akka.actor.{ Actor, ActorRef, Props }
import akka.pattern.pipe
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowInstanceExecution._
import com.flowtick.sysiphos.flow._
import com.flowtick.sysiphos.logging.Logger

import scala.concurrent.Future

class FlowInstanceExecutorActor(
  flowDefinition: FlowDefinition,
  flowInstance: FlowInstance,
  val flowInstanceRepository: FlowInstanceRepository,
  val flowTaskInstanceRepository: FlowTaskInstanceRepository,
  logger: Logger)(implicit repositoryContext: RepositoryContext)
  extends Actor with FlowInstanceExecution {

  implicit val executionContext = scala.concurrent.ExecutionContext.fromExecutor(Executors.newWorkStealingPool())

  def flowTaskExecutor(taskInstance: FlowTaskInstance): ActorRef =
    context.actorOf(Props(new FlowTaskExecutionActor(taskInstance, flowInstance, context.parent, logger)))

  def selfRef: ActorRef = self

  override def receive: PartialFunction[Any, Unit] = {
    case FlowInstanceExecution.Execute(optionalTask) =>
      log.info(s"executing $flowDefinition, $flowInstance ...")
      val executed: Future[FlowInstanceExecution.FlowInstanceMessage] = for {
        currentInstances <- flowTaskInstanceRepository.getFlowTaskInstances(Some(flowInstance.id), None, None)

        next: Seq[FlowTaskExecution.Execute] <- executeNext(
          flowDefinition,
          flowInstance,
          selectTask = optionalTask.flatMap(flowDefinition.findTask),
          currentInstances = currentInstances,
          logger)

        executionMessage <- next match {
          case Nil => instanceStatus
          case nextTasks: Seq[FlowTaskExecution.Execute] =>
            val result: Future[WorkTriggered] = Future.sequence(
              nextTasks.map(execute => Future.successful(execute).pipeTo(flowTaskExecutor(execute.taskInstance)))).map(WorkTriggered)
            result
        }
      } yield executionMessage

      executed.pipeTo(context.parent)

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

