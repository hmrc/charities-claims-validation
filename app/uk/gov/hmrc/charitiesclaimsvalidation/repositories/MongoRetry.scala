/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.repositories

import com.mongodb.{MongoNodeIsRecoveringException, MongoNotPrimaryException, MongoSocketException, MongoTimeoutException}
import play.api.Logging

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

object MongoRetry extends Logging {

  private val scheduler = Executors.newSingleThreadScheduledExecutor { (r: Runnable) =>
    val t = new Thread(r, "mongo-retry-scheduler")
    t.setDaemon(true)
    t
  }

  def isTransientError(ex: Throwable): Boolean = ex match
    case _: MongoTimeoutException          => true
    case _: MongoNotPrimaryException       => true
    case _: MongoNodeIsRecoveringException => true
    case _: MongoSocketException           => true
    case _                                 => false

  def withRetry[T](
    operationName: String,
    maxAttempts: Int,
    initialDelay: FiniteDuration,
    maxDelay: FiniteDuration
  )(operation: => Future[T])(implicit ec: ExecutionContext): Future[T] =

    def attempt(attemptsRemaining: Int, currentDelay: FiniteDuration): Future[T] =
      operation.recoverWith {
        case ex if attemptsRemaining > 1 && isTransientError(ex) =>
          val jitterMs        = (currentDelay.toMillis * Random.nextDouble() * 0.5).toLong
          val delayWithJitter = FiniteDuration(currentDelay.toMillis + jitterMs, TimeUnit.MILLISECONDS)
          val nextDelay       = FiniteDuration(math.min(currentDelay.toMillis * 2, maxDelay.toMillis), TimeUnit.MILLISECONDS)

          logger.warn(
            s"Transient MongoDB error during $operationName, " +
              s"retrying in ${delayWithJitter.toMillis}ms " +
              s"(${attemptsRemaining - 1} attempts remaining): ${ex.getMessage}"
          )

          delayFuture(delayWithJitter).flatMap(_ => attempt(attemptsRemaining - 1, nextDelay))
      }

    attempt(maxAttempts, initialDelay)

  private def delayFuture(duration: FiniteDuration): Future[Unit] =
    val promise = Promise[Unit]()
    scheduler.schedule(
      (() => promise.success(())): Runnable,
      duration.toMillis,
      TimeUnit.MILLISECONDS
    )
    promise.future
}
