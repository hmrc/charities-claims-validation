/*
 * Copyright 2023 HM Revenue & Customs
 *
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
