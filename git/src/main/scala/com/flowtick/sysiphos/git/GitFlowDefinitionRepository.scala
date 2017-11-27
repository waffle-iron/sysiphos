package com.flowtick.sysiphos.git

import java.io.{ByteArrayOutputStream, File, FileOutputStream}

import com.flowtick.sysiphos.flow.FlowDefinition.SysiphosDefinition
import com.flowtick.sysiphos.flow.{FlowDefinition, FlowDefinitionRepository}
import com.flowtick.sysiphos.task.CommandLineTask
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

class GitFlowDefinitionRepository(baseDir: File,
                                  remoteUrl: Option[String]) extends FlowDefinitionRepository {

  private def createFile(path: String, content: Array[Byte], git: Git): Try[File] = Try {
    val file = new File(git.getRepository.getDirectory.getParent, path)
    val fileStream = new FileOutputStream(file)
    fileStream.write(content)
    fileStream.flush()
    fileStream.close()
    file
  }

  private def addAndCommitFile(path: String, content: Array[Byte], message: String)(implicit git: Git): Try[Git] =
    for {
      _ <- createFile(path, content, git)
      _ <- Try(git.add().addFilepattern(path).call())
      _ <- Try(git.commit().setMessage(message).call())
    } yield git

  private def initSysiphosRepository(git: Git): Try[Git] = addAndCommitFile(".sysiphos", "empty".getBytes, "init")(git)

  def getOrCreateRepository: Try[Git] =
    if (!baseDir.exists())
      Try(Git.init().setDirectory(baseDir).call()).flatMap(git => initSysiphosRepository(git))
    else
      Try(new FileRepositoryBuilder().setGitDir(new File(baseDir, Constants.DOT_GIT)).readEnvironment().findGitDir().build()).map(Git.wrap)

  override def getFlowDefinitions: Future[Seq[FlowDefinition]] = {
    getOrCreateRepository.fold(error => Future.failed(error), (git: Git) => Future {

      val head = git.getRepository.findRef("HEAD")

      // a RevWalk allows to walk over commits based on some filtering that is defined
      val walk = new RevWalk(git.getRepository)
      val commit = walk.parseCommit(head.getObjectId)
      val tree = commit.getTree

      val treeWalk = new TreeWalk(git.getRepository)
      treeWalk.addTree(tree)
      treeWalk.setRecursive(false)

      val foundDefinitions = scala.collection.mutable.ListBuffer[FlowDefinition]()

      while (treeWalk.next()) {
        if (treeWalk.isSubtree) { // a directory
          treeWalk.enterSubtree()
        } else { // a file
          val objectId = treeWalk.getObjectId(0)
          val loader = git.getRepository.open(objectId)
          val content = new ByteArrayOutputStream()
          loader.copyTo(content)
          val definition = FlowDefinition.fromJson(content.toString)
          definition.right.foreach(foundDefinitions.append(_))
        }
      }
      foundDefinitions
    })
  }

  override def addFlowDefinition(flowDefinition: FlowDefinition): Future[_] =
    getOrCreateRepository.fold(Future.failed[Unit], (git: Git) => Future.fromTry {
      val definitionName = s"${flowDefinition.id}.json"
      addAndCommitFile(definitionName, FlowDefinition.toJson(flowDefinition).getBytes, s"add $definitionName")(git)
    })
}

object GitFlowDefinitionRepositoryApp extends App {
  val repository = new GitFlowDefinitionRepository(new File("target/git"), None)

  repository.addFlowDefinition(SysiphosDefinition(
    id = "foo",
    task = CommandLineTask("cmd", None, "ls -la")
  ))

  println(Await.result(repository.getFlowDefinitions, Duration.Inf))
}
