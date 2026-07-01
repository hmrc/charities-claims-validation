/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.{Created, InternalServerError}
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.AwaitingUploadStatus
import uk.gov.hmrc.charitiesclaimsvalidation.models.requests.UploadRequest
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.SuccessResponse
import uk.gov.hmrc.charitiesclaimsvalidation.repositories.ClaimValidationStatusRepository

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClaimService @Inject() (repository: ClaimValidationStatusRepository)(using ExecutionContext) extends Logging {

  def deleteClaims(claimId: String): Future[SuccessResponse] = {
    logger.info(s"Deleting all claims for claimId=$claimId")
    repository.deleteByClaimId(claimId).map(SuccessResponse(_))
  }

  def storeUploadStatus(claimId: String, uploadDetails: UploadRequest): Future[Result] = {
    val reference      = uploadDetails.reference.value
    val validationType = uploadDetails.validationType
    logger.info(s"Storing upload status for claimId=$claimId, reference=$reference, validationType=$validationType")
    repository.findByClaimIdAndValidationType(claimId, uploadDetails.validationType).flatMap {
      case Some(status: AwaitingUploadStatus) if ClaimService.uploadStatusExists(uploadDetails, status) =>
        logger.info(s"Upload status already exists for claimId=$claimId, reference=$reference, validationType=$validationType")
        Future.successful(ClaimService.SuccessfulUploadStatus)

      case Some(status: AwaitingUploadStatus) if ClaimService.uploadStatusDoesNotExist(uploadDetails, status) =>
        logger.info(
          s"Replacing existing upload status for claimId=$claimId, oldReference=${status.reference}, newReference=$reference, validationType=$validationType"
        )
        val newAwaitingStatus = ClaimService.createAwaitingUploadStatus(claimId, uploadDetails)
        for {
          _        <- repository.deleteByReference(claimId, status.reference)
          inserted <- repository.insert(newAwaitingStatus)
        } yield {
          if inserted then ClaimService.SuccessfulUploadStatus else ClaimService.UnsuccessfulUploadStatus
        }

      case None =>
        logger.info(s"Inserting new upload status for claimId=$claimId, reference=$reference, validationType=$validationType")
        repository
          .insert(ClaimService.createAwaitingUploadStatus(claimId, uploadDetails))
          .map { inserted =>
            if inserted then ClaimService.SuccessfulUploadStatus else ClaimService.UnsuccessfulUploadStatus
          }

      case _ =>
        logger.warn(
          s"Unexpected state when storing upload status for claimId=$claimId, reference=$reference, validationType=$validationType — rejecting request"
        )
        Future.successful(ClaimService.UnsuccessfulUploadStatus)
    }
  }

  def touchTtlByClaimId(claimId: String): Future[Unit] =
    repository.touchTtlByClaimId(claimId)
}

object ClaimService {

  val SuccessfulUploadStatus: Result   = Created(Json.obj("success" -> true))
  val UnsuccessfulUploadStatus: Result = InternalServerError(Json.obj("success" -> false))

  private def uploadStatusDoesNotExist(uploadDetails: UploadRequest, status: AwaitingUploadStatus) =
    status.reference != uploadDetails.reference.value && status.validationType == uploadDetails.validationType

  private def uploadStatusExists(uploadDetails: UploadRequest, status: AwaitingUploadStatus) =
    status.reference == uploadDetails.reference.value && status.validationType == uploadDetails.validationType

  private def createAwaitingUploadStatus(claimId: String, uploadDetails: UploadRequest) = {
    AwaitingUploadStatus(
      claimId = claimId,
      reference = uploadDetails.reference.value,
      validationType = uploadDetails.validationType,
      uploadUrl = uploadDetails.uploadUrl.toString,
      initiateTimestamp = Instant.now(),
      fields = uploadDetails.fields,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
  }
}
