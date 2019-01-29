package com.flowtick.sysiphos.logging

import java.nio.file.{ Files, Path }

import blobstore.fs.FileStore
import cats.effect.IO
import fs2.Pure
import org.scalatest.{ FlatSpec, Matchers }

class StreamLoggerSpec extends FlatSpec with Matchers {
  import Logger._

  val thousandStrings: fs2.Stream[Pure, LogId] = fs2.Stream.emits(Seq.tabulate(1000)(_.+(1).toString))

  trait TestFileStore {
    val tempDir: Path = Files.createTempDirectory("test")
    val fileStore: FileStore[IO] = FileStore[IO](java.nio.file.Paths.get(tempDir.toUri), logExecutionContext)
  }

  "Stream logger" should "append to files" in new TestFileStore {

    val streamLogger: StreamLogger = new StreamLogger("", fileStore) {
      override def format(line: String): String = "prefix " + line
    }

    val logId = "test"

    streamLogger.appendStream(logId, thousandStrings).unsafeRunSync()
    streamLogger.appendStream(logId, fs2.Stream.emits(Seq("1001"))).unsafeRunSync()

    val expectedStrings: fs2.Stream[Pure, LogId] = thousandStrings.append(fs2.Stream.emit("1001")).map("prefix " + _)

    streamLogger.getLog(logId).compile.toList.unsafeRunSync() should be(expectedStrings.toList)
  }
}
