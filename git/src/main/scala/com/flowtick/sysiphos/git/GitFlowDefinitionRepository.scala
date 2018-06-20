package com.flowtick.sysiphos.git

import java.io.File

import com.flowtick.sysiphos.core.RepositoryContext
import com.flowtick.sysiphos.flow.FlowDefinition._
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowDefinitionDetails, FlowDefinitionMetaData, FlowDefinitionRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GitFlowDefinitionRepository(
  baseDir: File,
  remoteUrl: Option[String],
  ref: Option[String],
  username: Option[String] = None,
  password: Option[String] = None,
  identityFilePath: Option[String] = None,
  identityFilePassphrase: Option[String] = None) extends AbstractGitRepository[FlowDefinition](baseDir, remoteUrl, ref, username, password, identityFilePath, identityFilePassphrase) with FlowDefinitionRepository {
  override def getFlowDefinitions(implicit repositoryContext: RepositoryContext): Future[Seq[FlowDefinitionDetails]] =
    list.map(definitions => definitions.map(FlowDefinitionDetails(_, FlowDefinitionMetaData(None, None, None))))

  override def addFlowDefinition(flowDefinition: FlowDefinition)(implicit repositoryContext: RepositoryContext): Future[FlowDefinitionDetails] =
    add(flowDefinition, s"${flowDefinition.id}.json").map(FlowDefinitionDetails(_, FlowDefinitionMetaData(None, None, None)))
}
