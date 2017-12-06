package com.flowtick.sysiphos.git

import java.io.File

import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.flow.{ FlowDefinition, FlowDefinitionRepository }
import com.flowtick.sysiphos.task.CommandLineTask

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global

class GitFlowDefinitionRepository(
  baseDir: File,
  remoteUrl: Option[String]) extends AbstractGitRepository[FlowDefinition](baseDir, remoteUrl) with FlowDefinitionRepository {
  override protected def fromString(stringValue: String): Either[Exception, FlowDefinition] = FlowDefinition.fromJson(stringValue)

  override protected def toString(item: FlowDefinition): String = FlowDefinition.toJson(item)

  override def getFlowDefinitions: Future[Seq[FlowDefinition]] = list

  override def addFlowDefinition(flowDefinition: FlowDefinition): Future[FlowDefinition] = add(flowDefinition, s"${flowDefinition.id}.json")
}

object GitFlowDefinitionRepositoryApp extends App {
  val repository = new GitFlowDefinitionRepository(new File("target/git"), None)

  repository.addFlowDefinition(SysiphosDefinition(
    id = "foo",
    task = CommandLineTask("cmd", None, "ls -la")))

  println(Await.result(repository.getFlowDefinitions, Duration.Inf))
}
