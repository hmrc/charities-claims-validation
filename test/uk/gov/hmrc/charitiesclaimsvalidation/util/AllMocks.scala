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

package uk.gov.hmrc.charitiesclaimsvalidation.util

import org.apache.pekko.actor.ActorSystem
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.charitiesclaimsvalidation.config.AppConfig
import uk.gov.hmrc.charitiesclaimsvalidation.controllers.actions.ClaimsAuthorisedAction
import uk.gov.hmrc.charitiesclaimsvalidation.repositories.ClaimValidationStatusRepository
import uk.gov.hmrc.charitiesclaimsvalidation.services.ClaimReferenceService
import uk.gov.hmrc.http.client.HttpClientV2

trait AllMocks extends MockitoSugar {
  me: BeforeAndAfterEach =>

  val mockActorSystem: ActorSystem                          = mock[ActorSystem]
  val mockAuthConnector: AuthConnector                      = mock[AuthConnector]
  val mockAppConfig: AppConfig                              = mock[AppConfig]
  val mockStatusRepository: ClaimValidationStatusRepository = mock[ClaimValidationStatusRepository]
  val mockHttpClient: HttpClientV2                          = mock[HttpClientV2]
  val mockClaimReferenceService: ClaimReferenceService      = mock[ClaimReferenceService]
  val mockAuthorisedFunctions: AuthorisedFunctions          = mock[AuthorisedFunctions]
  val mockClaimsAuthorisedAction: ClaimsAuthorisedAction    = mock[ClaimsAuthorisedAction]

  override protected def beforeEach(): Unit =
    Seq(
      mockActorSystem,
      mockAuthConnector,
      mockAppConfig,
      mockStatusRepository,
      mockHttpClient,
      mockClaimReferenceService,
      mockAuthorisedFunctions,
      mockClaimsAuthorisedAction
    ).foreach(Mockito.reset(_))
}
