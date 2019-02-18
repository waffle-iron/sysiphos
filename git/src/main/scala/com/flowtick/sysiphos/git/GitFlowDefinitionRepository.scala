package com.flowtick.sysiphos.git

import java.io.File

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.FlowDefinition._
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowDefinitionDetails, FlowDefinitionRepository }

import scala.concurrent.{ ExecutionContext, Future }

class GitFlowDefinitionRepository(
  baseDir: File,
  remoteUrl: Option[String],
  ref: Option[String],
  username: Option[String] = None,
  password: Option[String] = None,
  identityFilePath: Option[String] = None,
  identityFilePassphrase: Option[String] = None)(implicit executionContext: ExecutionContext) extends AbstractGitRepository[FlowDefinition](baseDir, remoteUrl, ref, username, password, identityFilePath, identityFilePassphrase) with FlowDefinitionRepository {
  override def getFlowDefinitions(implicit repositoryContext: RepositoryContext): Future[Seq[FlowDefinitionDetails]] =
    list.map(definitions => definitions.map(definition => FlowDefinitionDetails(definition.id, None, None, None)))

  override def createOrUpdateFlowDefinition(flowDefinition: FlowDefinition)(implicit repositoryContext: RepositoryContext): Future[FlowDefinitionDetails] =
    add(flowDefinition, s"${flowDefinition.id}.json").map(definition => FlowDefinitionDetails(definition.id, None, None, None))

  override def findById(id: String)(implicit repositoryContext: RepositoryContext): Future[Option[FlowDefinitionDetails]] =
    list.map(_.find(_.id == id).map(definition => FlowDefinitionDetails(definition.id, None, None, None)))

  override def delete(flowDefinitionId: String)(implicit repositoryContext: RepositoryContext): Future[Unit] = ???
}
