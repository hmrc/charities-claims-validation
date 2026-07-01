/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.repositories

import com.mongodb.{MongoNodeIsRecoveringException, MongoNotPrimaryException, MongoSocketException, MongoTimeoutException, ServerAddress}
import org.bson.BsonDocument
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

class MongoRetrySpec extends AnyWordSpec with Matchers with ScalaFutures:

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(30, Seconds), interval = Span(100, Millis))

  private val maxAttempts  = 3
  private val initialDelay = 50.millis
  private val maxDelay     = 500.millis
  private val transientErrors = Seq(
    new MongoTimeoutException("timeout"),
    new MongoSocketException("socket error", new ServerAddress()),
    new MongoNotPrimaryException(new BsonDocument(), new ServerAddress()),
    new MongoNodeIsRecoveringException(new BsonDocument(), new ServerAddress())
  )
  private val nonTransientErrors = Seq(
    new RuntimeException("not transient"),
    new IllegalArgumentException("bad arg")
  )

  "MongoRetry.withRetry" should:
    "succeed on first attempt without retrying" in:
      val result = MongoRetry.withRetry("test-op", maxAttempts, initialDelay, maxDelay) {
        Future.successful("success")
      }

      result.futureValue shouldBe "success"

    "retry each transient error type and eventually succeed" in:
      transientErrors.foreach { transientError =>
        withClue(s"${transientError.getClass.getSimpleName}: ") {
          val callCount = new AtomicInteger(0)

          val result = MongoRetry.withRetry("test-op", maxAttempts, initialDelay, maxDelay) {
            val attempt = callCount.incrementAndGet()
            if attempt == 1 then Future.failed(transientError)
            else Future.successful("recovered")
          }

          result.futureValue shouldBe "recovered"
          callCount.get() shouldBe 2
        }
      }

    "fail after exhausting all retry attempts on persistent transient error" in:
      val callCount = new AtomicInteger(0)

      val result = MongoRetry.withRetry("test-op", maxAttempts, initialDelay, maxDelay) {
        callCount.incrementAndGet()
        Future.failed(new MongoTimeoutException("persistent timeout"))
      }

      result.failed.futureValue shouldBe a[MongoTimeoutException]
      callCount.get() shouldBe maxAttempts

    "not retry non-transient errors" in:
      val callCount = new AtomicInteger(0)

      val result = MongoRetry.withRetry("test-op", maxAttempts, initialDelay, maxDelay) {
        callCount.incrementAndGet()
        Future.failed(new RuntimeException("permanent error"))
      }

      result.failed.futureValue shouldBe a[RuntimeException]
      callCount.get() shouldBe 1

    "not retry when maxAttempts is 1" in:
      val callCount = new AtomicInteger(0)

      val result = MongoRetry.withRetry("test-op", maxAttempts = 1, initialDelay, maxDelay) {
        callCount.incrementAndGet()
        Future.failed(new MongoTimeoutException("timeout"))
      }

      result.failed.futureValue shouldBe a[MongoTimeoutException]
      callCount.get() shouldBe 1

    "increase retry delay with exponential backoff" in:
      val callCount  = new AtomicInteger(0)
      val timestamps = new java.util.concurrent.ConcurrentLinkedQueue[Long]()

      val result = MongoRetry.withRetry("test-op", maxAttempts = 4, 100.millis, 2000.millis) {
        timestamps.add(System.currentTimeMillis())
        val attempt = callCount.incrementAndGet()
        if attempt < 4 then Future.failed(new MongoTimeoutException("timeout"))
        else Future.successful("done")
      }

      result.futureValue shouldBe "done"
      callCount.get() shouldBe 4

      val times  = timestamps.toArray.map(_.asInstanceOf[Long])
      val delays = times.sliding(2).map(pair => pair(1) - pair(0)).toSeq

      delays should have size 3
      delays(1) should be > delays.head
      delays(2) should be >= delays(1)

  "MongoRetry.isTransientError" should:
    "return true for known transient errors" in:
      transientErrors.foreach { ex =>
        withClue(s"${ex.getClass.getSimpleName}: ") {
          MongoRetry.isTransientError(ex) shouldBe true
        }
      }

    "return false for non-transient errors" in:
      nonTransientErrors.foreach { ex =>
        withClue(s"${ex.getClass.getSimpleName}: ") {
          MongoRetry.isTransientError(ex) shouldBe false
        }
      }
