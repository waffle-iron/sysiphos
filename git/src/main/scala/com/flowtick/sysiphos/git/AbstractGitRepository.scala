package com.flowtick.sysiphos.git

import java.io.{ ByteArrayOutputStream, File, FileOutputStream }

import com.jcraft.jsch.{ JSch, Session }
import io.circe.parser._
import io.circe.syntax._
import io.circe.{ Decoder, Encoder }
import org.eclipse.jgit.api.{ Git, TransportConfigCallback }
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport._
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.FS
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import com.flowtick.sysiphos._

abstract class AbstractGitRepository[T](
  workDir: File,
  remoteUrl: Option[String],
  ref: Option[String],
  username: Option[String],
  password: Option[String],
  identityFilePath: Option[String],
  identityFilePassphrase: Option[String])(implicit val executionContent: ExecutionContext, decoder: Decoder[T], encoder: Encoder[T]) {
  val logger: Logger = LoggerFactory.getLogger(getClass)

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

  protected def initRepository: Try[Git] = Try(Git.init().setDirectory(workDir).call()).flatMap(git => init(git))

  protected def sshSessionFactory: JschConfigSessionFactory =
    new JschConfigSessionFactory() {
      override def createDefaultJSch(fs: FS): JSch = {
        val defaultJsch = super.createDefaultJSch(fs)
        identityFilePath.foreach(path => defaultJsch.addIdentity(path, identityFilePassphrase.orNull))
        defaultJsch
      }

      override def configure(hc: OpenSshConfig.Host, session: Session): Unit = ()
    }

  protected def cloneRepo(remoteUrl: String): Try[Git] =
    Try {
      val cloneCommand = Git.cloneRepository()
        .setURI(remoteUrl)
        .setDirectory(workDir)
        .setTransportConfigCallback(new TransportConfigCallback {
          override def configure(transport: Transport): Unit = transport match {
            case ssh: SshTransport => ssh.setSshSessionFactory(sshSessionFactory)
            case _ =>
          }
        })

      for {
        u <- username
        p <- password
      } yield cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(u, p))

      cloneCommand.call()
    }

  def getWorkDirRepo: Try[Git] = Try {
    new FileRepositoryBuilder()
      .setGitDir(new File(workDir, Constants.DOT_GIT))
      .readEnvironment()
      .build()
  }.map(Git.wrap)

  protected def getOrCreate: Try[Git] =
    if (!workDir.exists()) {
      remoteUrl.map(cloneRepo).getOrElse(initRepository)
    } else getWorkDirRepo

  protected def add(item: T, name: String): Future[T] = {
    getOrCreate.fold(Future.failed, (git: Git) => Future.fromTry {
      addAndCommitFile(
        name,
        item.asJson.noSpaces.getBytes,
        s"add $name")(git).map(_ => item)
    })
  }

  def list: Future[Seq[T]] = {
    val repo = getOrCreate
    repo.fold(error => Future.failed(error), (git: Git) => Future {

      val head = git.getRepository.findRef(ref.getOrElse("master"))

      // a RevWalk allows to walk over commits based on some filtering that is defined
      val walk = new RevWalk(git.getRepository)
      val commit = walk.parseCommit(head.getObjectId)
      val tree = commit.getTree

      val treeWalk = new TreeWalk(git.getRepository)
      treeWalk.addTree(tree)
      treeWalk.setRecursive(false)

      val foundItems = scala.collection.mutable.ListBuffer[T]()

      while (treeWalk.next()) {
        if (treeWalk.isSubtree) { // a directory
          treeWalk.enterSubtree()
        } else { // a file
          val objectId = treeWalk.getObjectId(0)
          val path = treeWalk.getPathString

          if (path.toLowerCase.endsWith(".json")) {
            val loader = git.getRepository.open(objectId)
            val content = new ByteArrayOutputStream()
            loader.copyTo(content)
            val item = decode[T](content.toString)
            item.right.foreach(foundItems.append(_))
            item.left.foreach(error => logger.error(s"error while loading item ($path, $objectId), $content", error))
          }
        }
      }
      foundItems
    })
  }

  protected val initFileName = ".init"

  protected def init(git: Git): Try[Git] = addAndCommitFile(initFileName, "empty".getBytes, "init repository")(git)
}
