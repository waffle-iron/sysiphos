package com.flowtick

import scala.util.control.NonFatal
import scala.util.{ Either, Failure, Right, Success, Try }

package object sysiphos {
  implicit class PimpedTry[T](aTry: Try[T]) {
    def fold[U](fa: Throwable => U, fb: T => U): U = aTry match {
      case Success(value) =>
        try { fb(value) } catch { case NonFatal(e) => fa(e) }
      case Failure(exception) => fa(exception)
    }
  }

  implicit class PimpedEither[A, B](either: Either[A, B]) {
    /**
     * Binds the given function across `Right`.
     *
     *  @param f The function to bind across `Right`.
     */
    def flatMap[A1 >: A, B1](f: B => Either[A1, B1]): Either[A1, B1] = either match {
      case Right(b) => f(b)
      case _ => this.asInstanceOf[Either[A1, B1]]
    }

    /**
     * The given function is applied if this is a `Right`.
     *
     *  {{{
     *  Right(12).map(x => "flower") // Result: Right("flower")
     *  Left(12).map(x => "flower")  // Result: Left(12)
     *  }}}
     */
    def map[B1](f: B => B1): Either[A, B1] = either match {
      case Right(b) => Right(f(b))
      case _ => this.asInstanceOf[Either[A, B1]]
    }

    /**
     * Returns a `Some` containing the `Right` value
     *  if it exists or a `None` if this is a `Left`.
     *
     * {{{
     * Right(12).toOption // Some(12)
     * Left(12).toOption  // None
     * }}}
     */
    def toOption: Option[B] = either match {
      case Right(b) => Some(b)
      case _ => None
    }
  }
}
