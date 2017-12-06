package com.flowtick.sysiphos.git

import java.io.File

import com.flowtick.sysiphos.scheduler.{ CronSchedule, FlowSchedule, FlowScheduleRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }

class GitFlowScheduleRepository(
  baseDir: File,
  remoteUrl: Option[String]) extends AbstractGitRepository[FlowSchedule](baseDir, remoteUrl) with FlowScheduleRepository {
  override protected def fromString(stringValue: String): Either[Exception, FlowSchedule] = FlowSchedule.fromJson(stringValue)

  override protected def toString(item: FlowSchedule): String = FlowSchedule.toJson(item)

  override def getFlowSchedules: Future[Seq[FlowSchedule]] = list

  override def setDueDate(flowSchedule: FlowSchedule, due: Long): Future[Unit] = ???

  override def addFlowSchedule(flowDefinition: FlowSchedule): Future[FlowSchedule] = add(flowDefinition, s"${flowDefinition.flowDefinitionId}.json")
}

object GitFlowScheduleRepositoryApp extends App {
  val repository = new GitFlowScheduleRepository(new File("target/git"), None)

  repository.addFlowSchedule(CronSchedule(
    "test-schedule",
    "",
    "test-definition"))

  println(Await.result(repository.getFlowSchedules, Duration.Inf))
}
