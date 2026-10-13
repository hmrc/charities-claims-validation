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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.{Json, Writes}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.charitiesclaimsvalidation.controllers.actions.DefaultClaimsAuthorisedAction

import scala.concurrent.Future

trait ControllerSpec extends BaseSpec {

  def jsonRequest[A: Writes](method: String, url: String, body: A): FakeRequest[play.api.mvc.AnyContentAsJson] =
    FakeRequest(method, url)
      .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
      .withJsonBody(Json.toJson(body))

  def emptyRequest(method: String, url: String): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(method, url)

  override protected def afterAll(): Unit =
    actorSystem.terminate()

  trait AuthorisedFixture {
    val mockAuthConnector: AuthConnector = mock[AuthConnector]

    private val orgEnrolment = Enrolment(
      key = "HMRC-CHAR-ORG",
      identifiers = Seq(EnrolmentIdentifier("CHARID", "1234567890")),
      state = "Activated"
    )

    when(mockAuthConnector.authorise(any(), any())(any(), any()))
      .thenReturn(Future.successful(new ~(Some(AffinityGroup.Organisation), Enrolments(Set(orgEnrolment)))))

    val bodyParser       = play.api.mvc.BodyParsers.Default(play.api.test.Helpers.stubPlayBodyParsers)
    val authorisedAction = new DefaultClaimsAuthorisedAction(mockAuthConnector, bodyParser)
  }
}
