package com.flowtick.sysiphos.git

import java.io.File

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.scheduler.{ CronSchedule, FlowScheduleRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.circe.generic.auto._

final case class GitCronSchedule(
  id: String,
  expression: String,
  flowDefinitionId: String,
  flowTaskId: Option[String],
  nextDueDate: Option[Long],
  enabled: Option[Boolean]) extends CronSchedule

class GitFlowScheduleRepository(
  baseDir: File,
  remoteUrl: Option[String],
  ref: Option[String],
  username: Option[String] = None,
  password: Option[String] = None,
  identityFilePath: Option[String] = None,
  identityFilePassphrase: Option[String] = None)
  extends AbstractGitRepository[GitCronSchedule](
    baseDir, remoteUrl, ref, username, password, identityFilePath, identityFilePassphrase) with FlowScheduleRepository[CronSchedule] {
  override def getFlowSchedules()(implicit repositoryContext: RepositoryContext): Future[Seq[GitCronSchedule]] = list

  def addFlowSchedule(
    id: String,
    expression: String,
    flowDefinitionId: String,
    flowTaskId: Option[String],
    nextDueDate: Option[Long],
    enabled: Option[Boolean])(implicit repositoryContext: RepositoryContext): Future[CronSchedule] = add(GitCronSchedule(
    id = id,
    expression = expression,
    flowDefinitionId = flowDefinitionId,
    flowTaskId = flowTaskId,
    nextDueDate = nextDueDate,
    enabled = enabled), s"${flowDefinitionId}.json")
}