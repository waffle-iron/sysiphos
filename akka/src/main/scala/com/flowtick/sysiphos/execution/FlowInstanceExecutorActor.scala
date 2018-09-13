package com.flowtick.sysiphos.execution

import akka.actor.{ Actor, ActorRef, PoisonPill, Props }
import akka.pattern.pipe
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.execution.FlowInstanceExecution.{ ExecutionFailed, Finished, Retry, WorkTriggered }
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
          running <- {
            log.info(s"setting task ${taskInstance.id} to running")
            flowTaskInstanceRepository.setStatus(taskInstance.id, FlowTaskInstanceStatus.Running)
          }
        } yield running

        runningInstance.flatMap {
          case Some(instance) => Future.successful(FlowTaskExecution.Execute(task, instance))
          case None => Future.failed(new IllegalStateException("unable to set instance to running"))
        }.pipeTo(flowTaskExecutor())
      })
    }

  def flowTaskExecutor(): ActorRef = context.actorOf(Props(new FlowTaskExecutionActor))

  def selfRef: ActorRef = self

  def die() = if (context.children.isEmpty) { selfRef ! PoisonPill }

  override def receive: PartialFunction[Any, Unit] = {
    case FlowInstanceExecution.Execute =>
      log.info(s"executing $flowDefinition, $flowInstance ...")
      execute(selectTask = None).map { newExecutions =>
        if (newExecutions.isEmpty && context.children.isEmpty) {
          Finished(flowInstance)
        } else
          WorkTriggered(newExecutions)
      }.pipeTo(context.parent)

    case FlowInstanceExecution.WorkFailed(e, task, flowTaskInstance) =>
      log.warn(s"task ${flowTaskInstance.id} failed with ${e.getLocalizedMessage}")
      context.stop(sender())
      flowTaskInstanceRepository.setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Failed).flatMap {
        case Some(details) if flowTaskInstance.retries == 0 =>
          log.info(s"retries exceeded for ${details.taskId}. sending failed execution")
          Future.successful {
            context.parent ! ExecutionFailed(flowTaskInstance)
            die()
          }
        case Some(details) =>
          log.info(s"retry sent from instance ${details.taskId}.")
          context.parent ! Retry(task, flowTaskInstance)
          Future.successful(die())
        case None => Future.failed(new RuntimeException(s"unable to update status of ${flowTaskInstance.id}"))
      }
    case FlowInstanceExecution.WorkDone(flowTaskInstance) =>
      context.stop(sender())
      log.info(s"Work is done for task with id ${flowTaskInstance.taskId}.")

      flowTaskInstanceRepository.setStatus(flowTaskInstance.id, FlowTaskInstanceStatus.Done).map(_ => {
        FlowInstanceExecution.Execute
      }).pipeTo(selfRef)
  }

}

