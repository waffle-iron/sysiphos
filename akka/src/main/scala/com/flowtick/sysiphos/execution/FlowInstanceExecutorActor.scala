package com.flowtick.sysiphos.execution

import akka.actor.{ Actor, ActorRef, Props }
import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.{ FlowTask, FlowTaskInstance, _ }
import com.flowtick.sysiphos.task.CommandLineTask
import akka.pattern.pipe

import scala.concurrent.Future
import scala.sys.process._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Success, Try }

trait FlowInstanceExecution extends Logging {
  var taskDefinitions: List[FlowTask] = List.empty[FlowTask]

  val flowInstance: FlowInstance
  val flowInstanceRepository: FlowInstanceRepository[FlowInstance]
  val flowTaskInstanceRepository: FlowTaskInstanceRepository[FlowTaskInstance]

  def nextFlowTask(taskInstances: Seq[FlowTaskInstance]): Option[(FlowTaskInstance, FlowTask)] = {
    taskDefinitions.flatMap { task =>
      taskInstances.find(_.taskId == task.id).map { instance => (instance, task) }
    }.headOption
  }

  @scala.annotation.tailrec
  protected final def createTaskDefinitions(flowTask: List[FlowTask], list: List[FlowTask]): List[FlowTask] = {
    flowTask match {
      case Nil => list
      case xs :: tail => createTaskDefinitions(xs.children.map(_.toList).getOrElse(List.empty) ++ tail, list ++ List(xs))
    }
  }
}

class FlowInstanceExecutorActor(
  override val flowInstance: FlowInstance,
  override val flowInstanceRepository: FlowInstanceRepository[FlowInstance],
  override val flowTaskInstanceRepository: FlowTaskInstanceRepository[FlowTaskInstance])(implicit repositoryContext: RepositoryContext)
  extends Actor with FlowInstanceExecution {

  def execute(flowTaskInstances: Seq[FlowTaskInstance], flowTaskExecutionActor: ActorRef): Future[FlowTaskExecution.Execute] =
    nextFlowTask(flowTaskInstances) match {
      case Some((flowTaskInstance, flowTask)) =>
        flowTaskInstanceRepository.setStatus(flowTaskInstance.id, "running").map { _ =>
          FlowTaskExecution.Execute(flowTask, flowTaskInstance)
        }.pipeTo(flowTaskExecutionActor)
      case None =>
        context.stop(flowTaskExecutionActor)
        Future.failed(new RuntimeException(s"tried to run task but there was none for flow instance ${flowInstance.id}"))
    }

  def executeAfterUpdate(update: => Future[Unit]): Future[FlowTaskExecution.Execute] = {
    update.flatMap { _ =>
      flowTaskInstanceRepository.getFlowTaskInstances(flowInstance.id)
    }.flatMap { instances => execute(instances, flowTaskExecutor()) }
  }

  def flowTaskExecutor(): ActorRef = context.actorOf(Props(new FlowTaskExecutionActor))

  override def receive: PartialFunction[Any, Unit] = {
    case FlowInstanceExecution.Init(flowDefinition) =>
      taskDefinitions = createTaskDefinitions(List(flowDefinition.task), List.empty)
      // create task instances or pass the existing ones to WorkStart phase
      flowTaskInstanceRepository.getFlowTaskInstances(flowInstance.id)
        .flatMap { taskInstances =>
          if (taskInstances.isEmpty)
            flowTaskInstanceRepository.createFlowTaskInstances(flowInstance.id, taskDefinitions)
          else
            Future.successful(taskInstances)
        }.map(execute(_, flowTaskExecutor()))

    case FlowInstanceExecution.WorkFailed(e, flowTaskInstance) =>
      context.stop(sender())
      if (flowTaskInstance.retries == 0) {
        flowTaskInstanceRepository.setStatus(flowTaskInstance.id, "failed").flatMap { _ =>
          flowInstanceRepository.setStatus(flowInstance.id, "failed")
        }
      } else
        executeAfterUpdate(flowTaskInstanceRepository.setRetries(flowTaskInstance.id, flowTaskInstance.retries - 1))
      log.error(e.getLocalizedMessage)

    case FlowInstanceExecution.WorkDone(flowTaskInstance) =>
      context.stop(sender())
      log.info(s"Work is done for task with id ${flowTaskInstance.taskId}.")
      executeAfterUpdate(flowTaskInstanceRepository.setStatus(flowTaskInstance.id, "done"))
  }

}

object FlowInstanceExecution {
  case class Init(flowDefinition: FlowDefinition)
  case class WorkDone(flowTaskInstance: FlowTaskInstance)
  case class WorkFailed(e: Throwable, flowTaskInstance: FlowTaskInstance)
}

class FlowTaskExecutionActor extends Actor with Logging {

  override def receive: Receive = {
    case f @ FlowTaskExecution.Execute(CommandLineTask(id, _, command), flowTaskInstance) =>
      log.info(s"executing command with id $id")
      val result: Try[String] = Try { command !! }
      result match {
        case Failure(e) => sender() ! FlowInstanceExecution.WorkFailed(e, flowTaskInstance)
        case Success(value) =>
          log.info(value)
          sender() ! FlowInstanceExecution.WorkDone(flowTaskInstance)
      }
  }
}

object FlowTaskExecution {
  case class Execute(flowTask: FlowTask, flowTaskInstance: FlowTaskInstance)
}