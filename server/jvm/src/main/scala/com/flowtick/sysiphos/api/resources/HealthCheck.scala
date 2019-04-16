package com.flowtick.sysiphos.api.resources

import java.util.concurrent.TimeUnit

import cats.data.Validated._
import cats.data.ValidatedNel
import cats.effect.IO
import cats.implicits._
import com.flowtick.sysiphos.logging.Logger
import com.twitter.finagle.http.Status
import io.finch._
import io.finch.syntax._
import javax.sql.DataSource

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

trait HealthCheck {

  implicit def executionContext: ExecutionContext

  implicit lazy val cs = cats.effect.IO.contextShift(executionContext)
  implicit lazy val timer = cats.effect.IO.timer(executionContext)

  val healthCheckLogId = ".sysiphos"

  def healthEndpoint(dataSource: DataSource, logger: Logger): Endpoint[String] = get("health") {
    (for {
      logCheck <- logger
        .getLog(healthCheckLogId)
        .compile
        .drain
        .timeout(Duration(5, TimeUnit.SECONDS))
        .attempt

      connection <- IO(dataSource.getConnection.close())
        .timeout(Duration(5, TimeUnit.SECONDS))
        .attempt

      healthy <- {
        val logOk: ValidatedNel[String, Unit] = logCheck.fold(error => invalidNel("unable to check health log file: " + error), validNel)
        val dbOk: ValidatedNel[String, Unit] = connection.fold(error => invalidNel("unable to create a connection to the database: " + error), validNel)
        IO(dbOk.combine(logOk))
      }

    } yield healthy match {
      case Valid(_) => Ok("OK")
      case Invalid(errors) => Output.payload(errors.reduceLeft(_ + ", " + _), Status.InternalServerError)
    }).unsafeRunSync()
  }

}