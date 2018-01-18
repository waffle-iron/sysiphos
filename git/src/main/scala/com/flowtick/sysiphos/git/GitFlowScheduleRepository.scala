package com.flowtick.sysiphos.git

import java.io.File

import com.flowtick.sysiphos.scheduler.{ FlowSchedule, FlowScheduleRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GitFlowScheduleRepository(
  baseDir: File,
  remoteUrl: Option[String],
  ref: Option[String],
  username: Option[String] = None,
  password: Option[String] = None,
  identityFilePath: Option[String] = None,
  identityFilePassphrase: Option[String] = None)
  extends AbstractGitRepository[FlowSchedule](
    baseDir, remoteUrl, ref, username, password, identityFilePath, identityFilePassphrase) with FlowScheduleRepository {
  override protected def fromString(stringValue: String): Either[Exception, FlowSchedule] = FlowSchedule.fromJson(stringValue)

  override protected def toString(item: FlowSchedule): String = FlowSchedule.toJson(item)

  override def getFlowSchedules: Future[Seq[FlowSchedule]] = list

  override def setDueDate(flowSchedule: FlowSchedule, due: Long): Future[Unit] = ???

  override def addFlowSchedule(flowDefinition: FlowSchedule): Future[FlowSchedule] = add(flowDefinition, s"${flowDefinition.flowDefinitionId}.json")
}