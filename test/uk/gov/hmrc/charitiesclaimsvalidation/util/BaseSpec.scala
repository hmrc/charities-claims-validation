/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
