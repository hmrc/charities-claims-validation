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

package uk.gov.hmrc.charitiesclaimsvalidation.controllers.actions

import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers.*
import play.api.mvc.*
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments, UnsupportedAffinityGroup}
import uk.gov.hmrc.charitiesclaimsvalidation.util.BaseSpec

import scala.concurrent.{ExecutionContext, Future}

class ClaimsAuthorisedActionSpec extends BaseSpec {

  class Harness(authorisedAction: ClaimsAuthorisedAction) {
    def onPageLoad: Action[AnyContent] = authorisedAction { request =>
      Results.Ok(request.affinityGroup.toString())
    }
  }

  val bodyParser: BodyParsers.Default = BodyParsers.Default(Helpers.stubPlayBodyParsers)

  "AuthorisedAction" - {
    "create AuthorisedRequest when user has an Organisation affinity group" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .returning(
          Future.successful(
            `~`(
              Some(AffinityGroup.Organisation),
              Enrolments(
                Set(Enrolment("HMRC-CHAR-ORG", Seq(EnrolmentIdentifier("CHARID", "1234567890")), "Activated"))
              )
            )
          )
        )
      val authorisedAction =
        new DefaultClaimsAuthorisedAction(mockAuthConnector, bodyParser)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result) shouldBe OK
      contentAsString(result) shouldBe "Organisation"
    }

    "create AuthorisedRequest when user has an Agent affinity group" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .returning(
          Future.successful(
            `~`(
              Some(AffinityGroup.Agent),
              Enrolments(
                Set(Enrolment("HMRC-CHAR-AGENT", Seq(EnrolmentIdentifier("AGENTCHARID", "1234567890")), "Activated"))
              )
            )
          )
        )

      val authorisedAction =
        new DefaultClaimsAuthorisedAction(mockAuthConnector, bodyParser)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test"))
      status(result) shouldBe OK
      contentAsString(result) shouldBe "Agent"
    }

    "throw UnsupportedAffinityGroup when provided with incorrect enrolments" in {
      val mockAuthConnector: AuthConnector = mock[AuthConnector]

      (mockAuthConnector
        .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments])(using
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *, *)
        .returning(
          Future.successful(
            `~`(
              Some(AffinityGroup.Agent),
              Enrolments(
                Set.empty
              )
            )
          )
        )

      val authorisedAction =
        new DefaultClaimsAuthorisedAction(mockAuthConnector, bodyParser)

      val controller = new Harness(authorisedAction)
      val result     = controller.onPageLoad(FakeRequest("GET", "/test")).failed
      await(result) shouldBe UnsupportedAffinityGroup("Agent enrolment missing or not activated")
    }

  }
}
