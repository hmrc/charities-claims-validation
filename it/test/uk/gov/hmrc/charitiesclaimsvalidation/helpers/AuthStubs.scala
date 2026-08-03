/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.charitiesclaimsvalidation.helpers

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status.OK
import uk.gov.hmrc.charitiesclaimsvalidation.helpers.wiremock.WireMockServerHandler

trait AuthStubs { self: WireMockServerHandler =>
  def stubAuthenticate(): StubMapping =
    server.stubFor(
      post(urlEqualTo("/auth/authorise")).willReturn(
        aResponse()
          .withStatus(OK)
          .withBody(
            s"""
               |{
               |  "internalId": "id",
               |  "email": "test@test.com",
               |  "allEnrolments": [{
               |     "key": "HMRC-CHAR-ORG",
               |     "identifiers": [{
               |       "key": "CHARID",
               |       "value": "example"
               |     }]
               |     },
               |     {
               |     "key": "HMRC-CHAR-AGENT",
               |     "identifiers": [{
               |       "key": "AGENTCHARID",
               |       "value": "example"
               |     }]
               |  }],
               |  "affinityGroup" : "Organisation",
               |  "loginTimes": {
               |     "currentLogin": "2025-03-27T09:00:00.000Z",
               |     "previousLogin": "2025-03-01T12:00:00.000Z"
               |  }
               |}
             """.stripMargin
          )
      )
    )

}
