package com.flowtick.sysiphos

import cats.effect.{ IO, Timer }

package object api {
  import com.twitter.util.{ Future => TFuture, Promise => TPromise, Return, Throw }
  import scala.concurrent.{ Future => SFuture, Promise => SPromise, ExecutionContext }
  import scala.util.{ Success, Failure }

  /**
   * taken from https://finagle.github.io/finch/cookbook.html#converting-between-scala-futures-and-twitter-futures
   */
  implicit class RichTFuture[A](f: TFuture[A]) {
    def asScala(implicit e: ExecutionContext): SFuture[A] = {
      val p: SPromise[A] = SPromise()
      f.respond {
        case Return(value) => p.success(value)
        case Throw(exception) => p.failure(exception)
      }

      p.future
    }
  }

  implicit class RichSFuture[A](f: SFuture[A]) {
    def asTwitter(implicit e: ExecutionContext): TFuture[A] = {
      val p: TPromise[A] = new TPromise[A]
      f.onComplete {
        case Success(value) => p.setValue(value)
        case Failure(exception) => p.setException(exception)
      }

      p
    }
  }

  implicit class RichIO[A](ioa: IO[A]) {
    import cats.syntax.apply._
    import scala.concurrent.duration._

    def retryWithBackoff(maxRetries: Int)(initialDelay: FiniteDuration = 5.seconds, factor: Int = 2, maxDuration: FiniteDuration = 5.minutes)(implicit timer: Timer[IO]): IO[A] = {

      ioa.handleErrorWith { error =>
        if (maxRetries > 0)
          IO.sleep(initialDelay) *> retryWithBackoff(maxRetries - 1)((initialDelay * factor).min(maxDuration))
        else
          IO.raiseError(error)
      }
    }
  }

}
