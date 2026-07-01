/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.util

import org.scalatest.concurrent.ScalaFutures
import org.scalamock.scalatest.MockFactory
import org.apache.pekko.stream.Materializer
import org.apache.pekko.actor.ActorSystem
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.time.{Millis, Span}

import scala.concurrent.ExecutionContext

trait BaseSpec extends AnyFreeSpec with MockFactory with Matchers with BeforeAndAfterEach with ScalaFutures with OptionValues {

  given ec: ExecutionContext     = scala.concurrent.ExecutionContext.global
  given actorSystem: ActorSystem = ActorSystem("unit-tests")
  given mat: Materializer        = Materializer.createMaterializer(actorSystem)

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(1000, Millis)), interval = scaled(Span(50, Millis)))

}
