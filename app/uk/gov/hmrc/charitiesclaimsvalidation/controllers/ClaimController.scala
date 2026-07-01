/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.controllers

import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.charitiesclaimsvalidation.controllers.actions.ClaimsAuthorisedAction
import uk.gov.hmrc.charitiesclaimsvalidation.controllers.utils.JsonResultOps.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.requests.UploadRequest
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.ErrorResults.*
import uk.gov.hmrc.charitiesclaimsvalidation.services.{ClaimService, UploadSummaryService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton()
class ClaimController @Inject() (
  cc: ControllerComponents,
  authAction: ClaimsAuthorisedAction,
  claimsService: ClaimService,
  uploadSummaryService: UploadSummaryService
)(using ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def deleteClaims(claimId: String): Action[AnyContent] =
    authAction.async {
      claimsService
        .deleteClaims(claimId)
        .map { res =>
          Ok(Json.toJson(res))
        }
    }

  def uploadTracking(claimId: String): Action[JsValue] = authAction(parse.json).async { request =>
    request.body
      .validate[UploadRequest]
      .foldWithRequiredParam(
        onRequiredEmpty = param => {
          logger.warn(s"Upload tracking request missing required param for claimId=$claimId")
          EmptyRequiredParam(param)
        },
        onOtherInvalid = details => {
          logger.warn(s"Invalid upload tracking request for claimId=$claimId: $details")
          InvalidUploadRequest(claimId, details)
        }
      )(upload => claimsService.storeUploadStatus(claimId, upload))

  }

  def getUploadSummary(claimId: String): Action[AnyContent] =
    authAction.async { _ =>
      uploadSummaryService
        .getUploadSummary(claimId)
        .map {
          case Some(summary) =>
            logger.info(s"Retrieved upload summary for claimId=$claimId with ${summary.uploads.size} upload(s)")
            Ok(Json.toJson(summary))
          case None =>
            logger.warn(s"No uploads found for claimId=$claimId")
            notFound(ClaimDoesNotExist(claimId))
        }
        .recover { case ex =>
          logger.error(s"Error retrieving upload summary for claimId=$claimId: ${ex.getMessage}", ex)
          internalServerError(InternalServiceError("Currently experiencing issues with our service"))
        }
    }

  def touchTtl(claimId: String): Action[AnyContent] = authAction.async {
    claimsService
      .touchTtlByClaimId(claimId)
      .map(_ => NoContent)
  }
}
