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

package uk.gov.hmrc.charitiesclaimsvalidation.controllers

import play.api.Logging
import play.api.libs.json.JsValue
import play.api.mvc.{Action, ControllerComponents, Result}
import uk.gov.hmrc.charitiesclaimsvalidation.models.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.ErrorResults.*
import uk.gov.hmrc.charitiesclaimsvalidation.services.{CallbackResult, UpscanCallbackService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.charitiesclaimsvalidation.controllers.utils.JsonResultOps.*
import eu.timepit.refined.types.string.NonEmptyString

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class UpscanCallbackController @Inject() (
  callbackService: UpscanCallbackService,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging:

  def callback(claimId: String): Action[JsValue] = Action(parse.json).async { implicit request =>
    request.body
      .validate[UpscanCallbackRequest]
      .foldWithRequiredParam(
        onRequiredEmpty = param => EmptyRequiredParam(param),
        onOtherInvalid = details => {
          logger.warn(s"Invalid callback request for claimId=$claimId: $details")
          InvalidUpscanCallbackRequest(claimId, details)
        }
      ) { callbackRequest =>
        val reference = callbackRequest.reference
        callbackService
          .processCallback(claimId, callbackRequest)
          .map {
            case (result @ CallbackResult.StartValidation, status) =>
              callbackService
                .processValidation(claimId, callbackRequest, status)
                .recover { case ex =>
                  logger.error(s"Error during async validation for claimId=$claimId, reference=$reference: ${ex.getMessage}", ex)
                }
              toHttpResponse(claimId, reference)(result)

            case (result, _) =>
              toHttpResponse(claimId, reference)(result)
          }
          .recover { case ex =>
            logger.error(s"Error processing callback for claimId=$claimId, reference=${reference.value}: ${ex.getMessage}", ex)
            internalServerError(UpscanCallbackFailed(claimId, reference.value))
          }
      }
  }

  private def toHttpResponse(claimId: String, reference: NonEmptyString)(result: CallbackResult): Result =
    result match {
      case CallbackResult.Success | CallbackResult.StartValidation => NoContent
      case CallbackResult.NotFound                                 => notFound(NotFoundClaimReference(claimId, reference.value))
      case CallbackResult.UpdateFailed                             => internalServerError(UpscanCallbackUpdateFailed(claimId, reference.value))
    }
