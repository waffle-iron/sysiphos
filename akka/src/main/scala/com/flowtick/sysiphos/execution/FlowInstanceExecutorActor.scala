package com.flowtick.sysiphos.execution

import akka.actor.{ Actor, ActorRef, Props }
import akka.pattern.pipe
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowInstanceExecution.{ Failed, Retry }
import com.flowtick.sysiphos.flow._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FlowInstanceExecutorActor(
  flowDefinition: FlowDefinition,
  flowInstance: FlowInstance,
  flowInstanceRepository: FlowInstanceRepository,
  flowTaskInstanceRepository: FlowTaskInstanceRepository)(implicit repositoryContext: RepositoryContext)
  extends Actor with FlowInstanceExecution {

  def execute(selectTask: Option[FlowTask]): Future[Seq[FlowTaskExecution.Execute]] = flowTaskInstanceRepository
    .getFlowTaskInstances(flowInstance.id)
    .flatMap { currentInstances =>
      val nextTasks: Seq[FlowTask] = selectTask.map(Seq(_)).getOrElse(nextFlowTasks(flowDefinition, currentInstances))
      Future.sequence(nextTasks.map { task =>
        val taskInstanceFuture = currentInstances
          .find(_.taskId == task.id)
          .map(Future.successful)
          .getOrElse(flowTaskInstanceRepository.createFlowTaskInstance(flowInstance.id, task.id))

        val runningInstance = for {
          taskInstance <- taskInstanceFuture
          running <- flowTaskInstanceRepository.setStatus(taskInstance.id, FlowTaskInstanceStatus.Running)
        } yield running

        runningInstance.flatMap {
          case Some(instance) => Future.successful(FlowTaskExecution.Execute(task, instance))
          case None => Future.failed(new IllegalStateException("unable to set instance to running"))
        }.pipeTo(flowTaskExecutor())
      })
    }

  def flowTaskExecutor(): ActorRef = context.actorOf(Props(new FlowTaskExecutionActor))

  override def receive: PartialFunction[Any, Unit] = {
    case FlowInstanceExecution.Execute(_) =>
      log.info(s"executing $flowDefinition, $flowInstance ...")
      execute(selectTask = None)

    case FlowInstanceExecution.WorkFailed(e, task, flowTaskInstance) =>
      context.stop(sender())
      val handleFailedTask: Future[FlowInstanceExecution.FlowInstanceMessage] = if (flowTaskInstance.retries == 0) {
        flowTaskInstanceRepository.setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Failed).flatMap { _ =>
          flowInstanceRepository.setStatus(flowInstance.id, FlowInstanceStatus.Failed).map(_ => Failed(flowInstance))
        }
      } else flowTaskInstanceRepository.setRetries(flowTaskInstance.id, flowTaskInstance.retries - 1).flatMap {
        case Some(updatedTaskInstance) => Future.successful(Retry(task, updatedTaskInstance))
        case None => Future.failed(new IllegalStateException(s"unable to update task instance $flowTaskInstance"))
      }

      handleFailedTask.pipeTo(self)

      log.error(e.getLocalizedMessage)

    case FlowInstanceExecution.WorkDone(flowTaskInstance) =>
      context.stop(sender())
      log.info(s"Work is done for task with id ${flowTaskInstance.taskId}.")
      flowTaskInstanceRepository.setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Done)
      execute(selectTask = None)

    case FlowInstanceExecution.Retry(task, flowTaskInstance) =>
      log.info(s"retrying $task in $flowTaskInstance.")
      execute(selectTask = Some(task))
  }

}

