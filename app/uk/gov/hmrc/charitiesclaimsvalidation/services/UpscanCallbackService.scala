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

package uk.gov.hmrc.charitiesclaimsvalidation.services

import eu.timepit.refined.auto.autoUnwrap
import play.api.Logging
import uk.gov.hmrc.charitiesclaimsvalidation.models.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.{UpscanCallbackRequest, UpscanFailureRequest, UpscanSuccessRequest}
import uk.gov.hmrc.charitiesclaimsvalidation.repositories.ClaimValidationStatusRepository
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.{CommonFileValidation, CommunityBuildingValidationService, ConnectedCharitiesValidationService, GiftAidValidationService, OtherIncomeValidationService}

import java.time.Instant
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

sealed trait CallbackResult
object CallbackResult {
  case object Success         extends CallbackResult
  case object StartValidation extends CallbackResult
  case object NotFound        extends CallbackResult
  case object UpdateFailed    extends CallbackResult
}

@Singleton
class UpscanCallbackService @Inject() (
  commonFileValidation: CommonFileValidation,
  otherIncomeValidationService: OtherIncomeValidationService,
  connectedCharitiesValidationService: ConnectedCharitiesValidationService,
  communityBuildingValidationService: CommunityBuildingValidationService,
  giftAidValidationService: GiftAidValidationService,
  repository: ClaimValidationStatusRepository
)(implicit ec: ExecutionContext)
    extends Logging {

  def processCallback(
    claimId: String,
    callbackRequest: UpscanCallbackRequest
  ): Future[(CallbackResult, Option[ClaimValidationStatus])] =
    repository
      .findByReference(claimId, callbackRequest.reference)
      .flatMap {
        case None =>
          logger.warn(s"Claim/reference not found: claimId=$claimId, reference=${callbackRequest.reference}")
          Future.successful((CallbackResult.NotFound, None))

        case Some(existingStatus) =>
          handleExistingStatus(claimId, existingStatus, callbackRequest)
      }

  def processValidation(
    claimId: String,
    callbackRequest: UpscanCallbackRequest,
    existingStatusOpt: Option[ClaimValidationStatus]
  ): Future[Unit] =
    (callbackRequest, existingStatusOpt) match {

      case (success: UpscanSuccessRequest, Some(existingStatus)) =>
        commonFileValidation
          .validateFile(success, existingStatus.validationType)
          .flatMap {
            case Left(error) =>
              logger.error(s"CommonFileValidation failed: $error")
              handleValidationResult(
                claimId,
                existingStatus,
                (List(error), None)
              )

            case Right(_) =>
              handleSuccessfulValidation(
                claimId,
                success,
                existingStatus
              )
          }

      case _ =>
        logger.error(s"Error validating callback for claimId=$claimId, reference==${callbackRequest.reference}")
        Future.unit
    }

  private def handleSuccessfulValidation(
    claimId: String,
    success: UpscanSuccessRequest,
    existingStatus: ClaimValidationStatus
  ): Future[Unit] =
    existingStatus.validationType match {

      case ValidationType.OtherIncome =>
        otherIncomeValidationService
          .validate(success.downloadUrl)
          .flatMap { result =>
            handleValidationResult[OtherIncomeData](
              claimId,
              existingStatus,
              result,
              attachData = Some((status: ValidationFailedStatus, data: Option[OtherIncomeData]) => status.copy(otherIncomeData = data)),
              attachValidData = Some((status: ValidatedStatus, data: Option[OtherIncomeData]) => status.copy(otherIncomeData = data))
            )
          }

      case ValidationType.CommunityBuildings =>
        communityBuildingValidationService
          .validate(success.downloadUrl)
          .flatMap { result =>
            handleValidationResult[CommunityBuildingData](
              claimId,
              existingStatus,
              result,
              attachData = Some((status: ValidationFailedStatus, data: Option[CommunityBuildingData]) => status.copy(communityBuildingsData = data)),
              attachValidData = Some((status: ValidatedStatus, data: Option[CommunityBuildingData]) => status.copy(communityBuildingsData = data))
            )
          }

      case ValidationType.ConnectedCharities =>
        connectedCharitiesValidationService
          .validate(success.downloadUrl)
          .flatMap { result =>
            handleValidationResult[ConnectedCharitiesData](
              claimId,
              existingStatus,
              result,
              attachData = Some((status: ValidationFailedStatus, data: Option[ConnectedCharitiesData]) => status.copy(connectedCharitiesData = data)),
              attachValidData = Some((status: ValidatedStatus, data: Option[ConnectedCharitiesData]) => status.copy(connectedCharitiesData = data))
            )
          }

      case ValidationType.GiftAid =>
        giftAidValidationService
          .validate(success.downloadUrl)
          .flatMap { result =>
            handleValidationResult[GiftAidScheduleData](
              claimId,
              existingStatus,
              result,
              attachData = Some((status: ValidationFailedStatus, data: Option[GiftAidScheduleData]) => status.copy(giftAidScheduleData = data)),
              attachValidData = Some((status: ValidatedStatus, data: Option[GiftAidScheduleData]) => status.copy(giftAidScheduleData = data))
            )
          }
    }

  private def handleValidationResult[A](
    claimId: String,
    existingStatus: ClaimValidationStatus,
    result: (List[ValidationError], Option[A]),
    attachData: Option[(ValidationFailedStatus, Option[A]) => ClaimValidationStatus] = None,
    attachValidData: Option[(ValidatedStatus, Option[A]) => ClaimValidationStatus] = None
  ): Future[Unit] = {

    val (errors, data) = result

    val newStatus =
      (errors, data) match {
        case (errs, d @ Some(_)) if errs.nonEmpty =>
          attachData
            .map(
              _(
                ValidationFailedStatus(
                  claimId = existingStatus.claimId,
                  reference = existingStatus.reference,
                  validationType = existingStatus.validationType,
                  errors = errs,
                  createdAt = existingStatus.createdAt,
                  updatedAt = Instant.now()
                ),
                d
              )
            )
            .getOrElse(
              ValidationFailedStatus(
                claimId = existingStatus.claimId,
                reference = existingStatus.reference,
                validationType = existingStatus.validationType,
                errors = errs,
                createdAt = existingStatus.createdAt,
                updatedAt = Instant.now()
              )
            )

        case (Nil, d @ Some(_)) =>
          attachValidData
            .map(
              _(
                ValidatedStatus(
                  claimId = existingStatus.claimId,
                  reference = existingStatus.reference,
                  validationType = existingStatus.validationType,
                  createdAt = existingStatus.createdAt,
                  updatedAt = Instant.now()
                ),
                d
              )
            )
            .getOrElse(
              ValidatedStatus(
                claimId = existingStatus.claimId,
                reference = existingStatus.reference,
                validationType = existingStatus.validationType,
                createdAt = existingStatus.createdAt,
                updatedAt = Instant.now()
              )
            )

        case (errs, None) if errs.nonEmpty =>
          ValidationFailedStatus(
            claimId = existingStatus.claimId,
            reference = existingStatus.reference,
            validationType = existingStatus.validationType,
            errors = errs,
            createdAt = existingStatus.createdAt,
            updatedAt = Instant.now()
          )

        case _ =>
          ValidationFailedStatus(
            claimId = existingStatus.claimId,
            reference = existingStatus.reference,
            validationType = existingStatus.validationType,
            errors = Nil,
            createdAt = existingStatus.createdAt,
            updatedAt = Instant.now()
          )
      }

    updateStatus(claimId, existingStatus.reference, newStatus).map(_ => ())
  }

  private def handleExistingStatus(
    claimId: String,
    existingStatus: ClaimValidationStatus,
    callbackRequest: UpscanCallbackRequest
  ): Future[(CallbackResult, Option[ClaimValidationStatus])] = {

    val ACCEPTED_MIME_TYPE = "application/vnd.oasis.opendocument.spreadsheet"

    callbackRequest match {

      case req: UpscanSuccessRequest =>
        if !req.uploadDetails.fileMimeType.equals(ACCEPTED_MIME_TYPE) then
          logger.warn(
            s"MIME type rejected for claimId=$claimId, reference=${existingStatus.reference}," +
              s" mimeType=${req.uploadDetails.fileMimeType}, expected=$ACCEPTED_MIME_TYPE"
          )

          val newStatus = VerificationFailedStatus(
            claimId = existingStatus.claimId,
            reference = existingStatus.reference,
            validationType = existingStatus.validationType,
            createdAt = existingStatus.createdAt,
            updatedAt = Instant.now(),
            failureDetails = FailureDetails(
              failureReason = FailureReason.Rejected,
              message = s"MIME type ${req.uploadDetails.fileMimeType} is not allowed"
            )
          )

          updateStatus(claimId, existingStatus.reference, newStatus)
            .map(result => (result, Some(existingStatus)))
        else if existingStatus.fileStatus.equalsIgnoreCase("AWAITING_UPLOAD") ||
          existingStatus.fileStatus.equalsIgnoreCase("VERIFYING")
        then
          logger.info(
            s"File accepted, starting validation for claimId=$claimId, reference=${existingStatus.reference}," +
              s" validationType=${existingStatus.validationType}, fileName=${req.uploadDetails.fileName}"
          )

          val newStatus = ValidatingStatus(
            claimId = existingStatus.claimId,
            reference = existingStatus.reference,
            validationType = existingStatus.validationType,
            createdAt = existingStatus.createdAt,
            updatedAt = Instant.now()
          )

          updateStatus(claimId, existingStatus.reference, newStatus)
            .map(result => (result, Some(existingStatus)))
        else {
          logger.info(
            s"Callback received but no action required for claimId=$claimId, reference=${existingStatus.reference}, fileStatus=${existingStatus.fileStatus}"
          )
          Future.successful((CallbackResult.Success, None))
        }

      case req: UpscanFailureRequest =>
        logger.warn(
          s"Upscan reported upload failure for claimId=$claimId, reference=${existingStatus.reference}," +
            s" failureReason=${req.failureDetails.failureReason}, message=${req.failureDetails.message}"
        )
        val newStatus = VerificationFailedStatus(
          claimId = existingStatus.claimId,
          reference = existingStatus.reference,
          validationType = existingStatus.validationType,
          createdAt = existingStatus.createdAt,
          updatedAt = Instant.now(),
          failureDetails = req.failureDetails
        )

        updateStatus(claimId, existingStatus.reference, newStatus)
          .map(result => (result, Some(existingStatus)))
    }
  }

  private def updateStatus(
    claimId: String,
    reference: String,
    newStatus: ClaimValidationStatus
  ): Future[CallbackResult] =
    repository.update(claimId, reference, newStatus).map { updated =>
      if updated then
        logger.info(s"Updated status for claimId=$claimId, reference=$reference to ${newStatus.fileStatus}")
        newStatus match

          case status: ValidatingStatus =>
            CallbackResult.StartValidation
          case _ =>
            CallbackResult.Success
      else
        logger.warn(s"Race condition: document existed during find but not during update for claimId=$claimId, reference=$reference")
        CallbackResult.UpdateFailed
    }

}
