package com.flowtick.sysiphos.git

import java.io.File
import java.util.UUID

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.scheduler.{ FlowScheduleDetails, FlowScheduleRepository }
import io.circe.generic.auto._

import scala.concurrent.{ ExecutionContext, Future }

class GitFlowScheduleRepository(
  baseDir: File,
  remoteUrl: Option[String],
  ref: Option[String],
  username: Option[String] = None,
  password: Option[String] = None,
  identityFilePath: Option[String] = None,
  identityFilePassphrase: Option[String] = None)(implicit executionContext: ExecutionContext)
  extends AbstractGitRepository[FlowScheduleDetails](
    baseDir, remoteUrl, ref, username, password, identityFilePath, identityFilePassphrase) with FlowScheduleRepository {
  override def getFlowSchedules(enabled: Option[Boolean], flowId: Option[String])(implicit repositoryContext: RepositoryContext): Future[Seq[FlowScheduleDetails]] =
    list.map(schedules => {
      (if (enabled.contains(true)) schedules.filter(_.enabled.contains(true)) else schedules).filter(schedule => flowId.forall(_ == schedule.flowDefinitionId))
    })

  def createFlowSchedule(
    id: Option[String],
    expression: Option[String],
    flowDefinitionId: String,
    flowTaskId: Option[String],
    enabled: Option[Boolean],
    backFill: Option[Boolean])(implicit repositoryContext: RepositoryContext): Future[FlowScheduleDetails] = {
    val newSchedule = FlowScheduleDetails(
      id = id.getOrElse(UUID.randomUUID().toString),
      expression = expression,
      flowDefinitionId = flowDefinitionId,
      flowTaskId = flowTaskId,
      nextDueDate = None,
      enabled = enabled,
      creator = repositoryContext.currentUser,
      created = repositoryContext.epochSeconds,
      version = 0,
      updated = None,
      backFill = backFill)
    add(newSchedule, s"$flowDefinitionId.json")
  }

  override def updateFlowSchedule(
    id: String,
    expression: Option[String],
    enabled: Option[Boolean],
    backFill: Option[Boolean])(implicit repositoryContext: RepositoryContext): Future[FlowScheduleDetails] = ???

  override def findById(id: String)(implicit repositoryContext: RepositoryContext): Future[Option[FlowScheduleDetails]] =
    getFlowSchedules(None, None).map(_.find(_.id == id))

  override def delete(id: String)(implicit repositoryContext: RepositoryContext): Future[Unit] = ???
}