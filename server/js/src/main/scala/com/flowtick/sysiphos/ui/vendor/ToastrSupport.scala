package com.flowtick.sysiphos.ui.vendor

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.util.{ Failure, Success }
import scala.concurrent.ExecutionContext.Implicits.global

object ToastrSupport {
  implicit class ToastrFuture[T](future: Future[T]) {
    def notifyError: Future[T] = errorMessage(_.getMessage)

    def withMessages(successMessage: Option[T => String], errorMessage: Option[Throwable => String]): Future[T] =
      future.andThen {
        case Success(value) =>
          successMessage.map(_(value)).foreach(message => Toastr.success(message))
        case Failure(error) =>
          errorMessage.map(_(error)).foreach(message => Toastr.error(message.take(1024)))
      }

    def errorMessage(message: Throwable => String): Future[T] = withMessages(None, Some(message))
    def successMessage(message: T => String): Future[T] = withMessages(Some(message), None)
  }
}

@JSGlobal("toastr")
@js.native
object Toastr extends js.Any {
  def info(message: String, title: String = null, options: js.Dictionary[_] = null): js.Any = js.native
  def warning(message: String, title: String = null, options: js.Dictionary[_] = null): js.Any = js.native
  def error(message: String, title: String = null, options: js.Dictionary[_] = null): js.Any = js.native
  def success(message: String, title: String = null, options: js.Dictionary[_] = null): js.Any = js.native
}
