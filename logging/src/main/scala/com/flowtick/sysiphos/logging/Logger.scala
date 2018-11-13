package com.flowtick.sysiphos.logging

import java.io.File
import java.nio.file.Paths
import java.util.concurrent.Executors

import cats.effect.{ ContextShift, IO }
import com.amazonaws.auth.{ AWSCredentialsProviderChain, AWSStaticCredentialsProvider, BasicAWSCredentials, DefaultAWSCredentialsProviderChain }
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3ClientBuilder }
import com.flowtick.sysiphos.config.Configuration.propOrEnv
import com.flowtick.sysiphos.logging.Logger.LogId
import org.slf4j
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.Try

trait Logger {
  /**
   *
   * @param logId the id to create the sink for
   * @return an fs2 Sink to be used for all append operations, consuming bytes via IO
   */
  protected def sink(logId: LogId): fs2.Sink[IO, Byte]

  /**
   *
   * @param logId the id to look up the source for
   * @return the source of a log id. This will depend on the underlying implementation of the
   *         sink, as cant know if it will append the data or not.
   *         If not (like in blobstores), the source should provided the data the stream will append to.
   *
   *         Defaults to an empty stream, thus assuming that the sink will append the data.
   */
  protected def source(logId: LogId): fs2.Stream[IO, String] = fs2.Stream.empty

  /**
   * @param key the key to create the ID for
   * @return return a LogId to be able to reference a specific log.
   *         only this ID and no out of band information should be used
   *         to write to or retrieve logs
   */
  def logId(key: String): Try[Logger.LogId]

  /**
   * @return a pipe to do custom processing on the stream without needing to overwrite #appendStream
   */
  def pipe: fs2.Pipe[IO, String, String] = identity

  /**
   * shortcut to append a single message via #appendStream
   *
   * @param logId see #appendStream
   * @param message the message to append
   * @return an IO representing the append
   */
  def appendLine(logId: Logger.LogId, message: String): IO[Unit] =
    appendStream(logId, fs2.Stream.emit(message))

  /**
   * Append a stream of lines to the sink and convert it to an IO
   *
   * @param logId id of the log to append to
   * @param lines a stream of lines that shoud be appended to the stream
   * @return an IO representing the append
   */
  def appendStream(logId: Logger.LogId, lines: fs2.Stream[IO, String]): IO[Unit] = {
    source(logId)
      .append(lines)
      .through(pipe)
      .through(fs2.text.utf8Encode)
      .to(sink(logId))
      .compile
      .drain
  }

  /**
   * @param logId id of the log to retrieve
   * @return a stream of log lines
   */
  def getLog(logId: Logger.LogId): Logger.LogStream
}

object Logger {
  val log: slf4j.Logger = LoggerFactory.getLogger(getClass)

  import blobstore.fs.FileStore
  import blobstore.s3.S3Store
  import blobstore.Store

  type LogId = String
  type LogStream = fs2.Stream[IO, String]

  private[logging] implicit val logExecutionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newWorkStealingPool())
  private[logging] implicit val contextShift: ContextShift[IO] = cats.effect.IO.contextShift(logExecutionContext)

  private def baseDirDefault: LogId = sys.props.get("java.io.tmpdir").map(_ + s"${File.separatorChar}sysiphos").getOrElse(s"${File.separatorChar}tmp")
  private def baseDirPath: String = propOrEnv("logger.file.baseDir", baseDirDefault)

  private def s3AccessKey: String = propOrEnv("logger.s3.accessKey", "changeme")
  private def s3SecretKey: String = propOrEnv("logger.s3.secretKey", "changeme")
  private def s3Bucket: String = propOrEnv("logger.s3.bucket", "changeme")

  private def streamChunkSize: Int = propOrEnv("logger.stream.chunkSize", "100").toInt

  def defaultLogger: Logger = propOrEnv("logger.impl", "file-direct").toLowerCase match {
    case "file-direct" =>
      new FileLogger(new File(baseDirPath))(logExecutionContext)

    case "s3" =>
      val s3: AmazonS3 = AmazonS3ClientBuilder
        .standard()
        .withCredentials(
          new AWSCredentialsProviderChain(
            DefaultAWSCredentialsProviderChain.getInstance(),
            new AWSStaticCredentialsProvider(new BasicAWSCredentials(s3AccessKey, s3SecretKey)))).build()

      val s3Store: Store[IO] = S3Store[IO](s3, blockingExecutionContext = logExecutionContext)
      new StreamLogger(s3Bucket, s3Store, streamChunkSize)

    case "file-stream" =>
      val fsStore = FileStore[IO](Paths.get(baseDirPath), logExecutionContext)
      new StreamLogger(pathRoot = "", fsStore, streamChunkSize)

    case "console" => new ConsoleLogger

    case other: String =>
      log.warn(s"unknown log implementation $other, default to console logger")
      new ConsoleLogger
  }
}
