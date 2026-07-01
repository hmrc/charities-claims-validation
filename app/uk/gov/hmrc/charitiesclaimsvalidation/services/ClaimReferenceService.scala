/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.{Created, InternalServerError}
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*
import java.time.temporal.ChronoUnit
import uk.gov.hmrc.charitiesclaimsvalidation.models.requests.UploadRequest
import uk.gov.hmrc.charitiesclaimsvalidation.repositories.ClaimValidationStatusRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClaimReferenceService @Inject (repository: ClaimValidationStatusRepository)(implicit
  val ec: ExecutionContext
) extends Logging {

  def storeUploadStatus(claimId: String, uploadDetails: UploadRequest)(using hc: HeaderCarrier): Future[Result] = {
    val reference = uploadDetails.reference.value
    logger.info(s"Storing upload status for claimId=$claimId, reference=$reference, validationType=${uploadDetails.validationType}")
    for {
      dbRes <- repository.insert(
        AwaitingUploadStatus(
          claimId = claimId,
          reference = reference,
          validationType = uploadDetails.validationType,
          uploadUrl = uploadDetails.uploadUrl.toString,
          initiateTimestamp = uploadDetails.initiateTimestamp,
          fields = uploadDetails.fields,
          createdAt = Instant.now(),
          updatedAt = Instant.now()
        )
      )
      response =
        if dbRes then Created(Json.obj("success" -> true))
        else InternalServerError(Json.obj("success" -> false))
    } yield response
  }

  def deleteByReference(claimId: String, reference: String): Future[Either[NotFoundClaimReference, SuccessResponse]] = {
    logger.info(s"Deleting upload for claimId=$claimId, reference=$reference")
    repository
      .deleteByReference(claimId, reference)
      .map { isDeleted =>
        if (isDeleted) {
          logger.info(s"Successfully deleted upload for claimId=$claimId, reference=$reference")
          Right(SuccessResponse(isDeleted))
        } else {
          logger.warn(s"Upload not found for deletion: claimId=$claimId, reference=$reference")
          Left(NotFoundClaimReference(claimId, reference))
        }
      }
  }

  def updateStatusByReference(
    claimId: String,
    reference: String,
    currentStatus: FileStatus,
    requestedStatus: FileStatus
  ): Future[Either[NotFoundClaimReference, SuccessResponse]] = {
    logger.info(s"Updating status for claimId=$claimId, reference=$reference, currentStatus=$currentStatus, requestedStatus=$requestedStatus")
    repository.findByReference(claimId, reference).flatMap {
      case None =>
        logger.warn(s"Upload not found for status update: claimId=$claimId, reference=$reference")
        Future.successful(Left(NotFoundClaimReference(claimId, reference)))

      case Some(validationStatus) if validationStatus.fileStatus != currentStatus.value =>
        logger.info(s"Status already transitioned for claimId=$claimId, reference=$reference, currentFileStatus=${validationStatus.fileStatus}")
        Future.successful(Right(SuccessResponse(success = true)))

      case Some(_) =>
        repository
          .updateFileStatusIfCurrentStatusMatches(claimId, reference, currentStatus, requestedStatus)
          .map { updated =>
            logger.info(s"Status update for claimId=$claimId, reference=$reference: updated=$updated, requestedStatus=$requestedStatus")
            Right(SuccessResponse(updated))
          }
    }
  }

  private val expiryDays = 7

  def getUploadResult(claimId: String, reference: String): Future[Either[ErrorResponse, UploadResultResponse]] =
    repository.findByReference(claimId, reference).flatMap {
      case None =>
        logger.info(s"Upload result not found for claimId=$claimId, reference=$reference")
        Future.successful(Left(NotFoundClaimReference(claimId, reference)))

      case Some(status: AwaitingUploadStatus) if isExpired(status) =>
        logger.info(s"Upload result expired for claimId=$claimId, reference=$reference, deleting...")
        repository.deleteByReference(claimId, reference).map { deleted =>
          if (!deleted) {
            logger.warn(
              s"Failed to delete expired upload for claimId=$claimId, reference=$reference - document may have been deleted by concurrent request"
            )
          }
          Left(ExpiredClaimReference(claimId, reference))
        }

      case Some(status) =>
        logger.info(s"Upload result found for claimId=$claimId, reference=$reference, fileStatus=${status.fileStatus}")
        Future.successful(Right(UploadResultResponse.fromClaimValidationStatus(status)))
    }

  private def isExpired(status: AwaitingUploadStatus): Boolean = {
    val daysSinceCreation = ChronoUnit.DAYS.between(status.createdAt, Instant.now())
    daysSinceCreation >= expiryDays
  }
}
