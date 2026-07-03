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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.charitiesclaimsvalidation.controllers.actions.ClaimsAuthorisedAction
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{FileStatus, FileStatusUpdateRequest}
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.ErrorResults.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.UploadResultResponse.given
import uk.gov.hmrc.charitiesclaimsvalidation.services.ClaimReferenceService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClaimReferenceController @Inject() (
  authorise: ClaimsAuthorisedAction,
  claimReferenceService: ClaimReferenceService,
  val cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def deleteByReference(claimId: String, reference: String): Action[AnyContent] = authorise.async { _ =>
    claimReferenceService
      .deleteByReference(claimId, reference)
      .map {
        case Left(error) => notFound(error)
        case Right(res)  => Ok(Json.toJson(res))
      }
  }

  def getUploadResult(claimId: String, reference: String): Action[AnyContent] = authorise.async { _ =>
    claimReferenceService
      .getUploadResult(claimId, reference)
      .map {
        case Left(error: ExpiredClaimReference)  => notFound(error)
        case Left(error: NotFoundClaimReference) => notFound(error)
        case Left(error)                         => internalServerError(error)
        case Right(result)                       => Ok(Json.toJson(result))
      }
      .recover { case ex: Exception =>
        logger.error(s"Error retrieving upload result for claimId=$claimId, reference=$reference", ex)
        internalServerError(InternalServiceError(ex.getMessage))
      }
  }

  def updateFileStatus(claimId: String, reference: String): Action[JsValue] = {
    authorise.async(parse.json) { request =>
      request.body
        .validate[FileStatusUpdateRequest]
        .fold(
          errors => {
            logger.warn(s"Invalid update file status request for claimId=$claimId, reference=$reference: ${errors.mkString(", ")}")
            Future.successful(badRequest(InvalidUpdateFileStatusRequest(claimId, reference, errors.mkString(", "))))
          },
          reqBody => handleFileStatusUpdate(claimId, reference, reqBody.fileStatus)
        )
    }
  }

  private def handleFileStatusUpdate(
    claimId: String,
    reference: String,
    requestedStatus: FileStatus
  ): Future[Result] = requestedStatus match {
    case FileStatus.Verifying =>
      claimReferenceService
        .updateStatusByReference(claimId, reference, FileStatus.AwaitingUpload, requestedStatus)
        .map {
          case Left(error)            => notFound(error)
          case Right(successResponse) => Ok(Json.toJson(successResponse))
        }
    case _ =>
      logger.warn(s"Invalid file status requested for claimId=$claimId, reference=$reference, requestedStatus=$requestedStatus")
      Future.successful(badRequest(InvalidFileStatus(claimId, reference, requestedStatus)))
  }
}
