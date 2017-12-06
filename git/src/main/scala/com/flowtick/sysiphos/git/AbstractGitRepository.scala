package com.flowtick.sysiphos.git

import java.io.{ ByteArrayOutputStream, File, FileOutputStream }

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

abstract class AbstractGitRepository[T](
  baseDir: File,
  remoteUrl: Option[String])(implicit val executionContent: ExecutionContext) {
  protected def createFile(path: String, content: Array[Byte], git: Git): Try[File] = Try {
    val file = new File(git.getRepository.getDirectory.getParent, path)
    val fileStream = new FileOutputStream(file)
    fileStream.write(content)
    fileStream.flush()
    fileStream.close()
    file
  }

  protected def addAndCommitFile(path: String, content: Array[Byte], message: String)(implicit git: Git): Try[Git] =
    for {
      _ <- createFile(path, content, git)
      _ <- Try(git.add().addFilepattern(path).call())
      _ <- Try(git.commit().setMessage(message).call())
    } yield git

  protected def getOrCreate: Try[Git] =
    if (!baseDir.exists())
      Try(Git.init().setDirectory(baseDir).call()).flatMap(git => init(git))
    else
      Try(new FileRepositoryBuilder().setGitDir(new File(baseDir, Constants.DOT_GIT)).readEnvironment().findGitDir().build()).map(Git.wrap)

  protected def add(item: T, name: String): Future[T] = {
    getOrCreate.fold(Future.failed, (git: Git) => Future.fromTry {
      addAndCommitFile(
        name,
        toString(item).getBytes,
        s"add $name")(git).map(_ => item)
    })
  }

  def list: Future[Seq[T]] = {
    getOrCreate.fold(error => Future.failed(error), (git: Git) => Future {

      val head = git.getRepository.findRef("HEAD")

      // a RevWalk allows to walk over commits based on some filtering that is defined
      val walk = new RevWalk(git.getRepository)
      val commit = walk.parseCommit(head.getObjectId)
      val tree = commit.getTree

      val treeWalk = new TreeWalk(git.getRepository)
      treeWalk.addTree(tree)
      treeWalk.setRecursive(false)

      val foundDefinitions = scala.collection.mutable.ListBuffer[T]()

      while (treeWalk.next()) {
        if (treeWalk.isSubtree) { // a directory
          treeWalk.enterSubtree()
        } else { // a file
          val objectId = treeWalk.getObjectId(0)
          val loader = git.getRepository.open(objectId)
          val content = new ByteArrayOutputStream()
          loader.copyTo(content)
          val definition = fromString(content.toString)
          definition.right.foreach(foundDefinitions.append(_))
        }
      }
      foundDefinitions
    })
  }

  protected val initFileName = ".sysiphos"

  protected def init(git: Git): Try[Git] = addAndCommitFile(initFileName, "empty".getBytes, "init")(git)

  protected def fromString(stringValue: String): Either[Exception, T]
  protected def toString(item: T): String
}
