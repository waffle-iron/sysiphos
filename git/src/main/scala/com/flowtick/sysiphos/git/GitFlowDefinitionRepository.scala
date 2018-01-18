package com.flowtick.sysiphos.git

import java.io.File

import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowDefinitionRepository }

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
  override protected def fromString(stringValue: String): Either[Exception, FlowDefinition] = FlowDefinition.fromJson(stringValue)

  override protected def toString(item: FlowDefinition): String = FlowDefinition.toJson(item)

  override def getFlowDefinitions: Future[Seq[FlowDefinition]] = list

  override def addFlowDefinition(flowDefinition: FlowDefinition): Future[FlowDefinition] = add(flowDefinition, s"${flowDefinition.id}.json")
}
