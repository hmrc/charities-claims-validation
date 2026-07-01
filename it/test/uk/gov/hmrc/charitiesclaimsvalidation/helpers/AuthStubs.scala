/*
 * Copyright 2023 HM Revenue & Customs
 *
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
