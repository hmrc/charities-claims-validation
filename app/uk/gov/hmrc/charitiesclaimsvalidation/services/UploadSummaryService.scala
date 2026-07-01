/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services

import play.api.Logging
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{AwaitingUploadStatus, ClaimValidationStatus}
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.{UploadSummaryItem, UploadSummaryResponse}
import uk.gov.hmrc.charitiesclaimsvalidation.repositories.ClaimValidationStatusRepository

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UploadSummaryService @Inject() (
  repository: ClaimValidationStatusRepository
)(implicit ec: ExecutionContext)
    extends Logging {

  private val ExpiryDays = 7

  def getUploadSummary(claimId: String): Future[Option[UploadSummaryResponse]] =
    repository.getClaimSummary(claimId).flatMap {
      case None => Future.successful(None)
      case Some(summary) =>
        purgeExpiredUploads(claimId, summary.uploads).map { remainingUploads =>
          if (remainingUploads.isEmpty) {
            None
          } else {
            val summaryItems = remainingUploads.map(UploadSummaryItem.fromClaimValidationStatus)
            Some(UploadSummaryResponse(summaryItems))
          }
        }
    }

  private def purgeExpiredUploads(
    claimId: String,
    uploads: Seq[ClaimValidationStatus]
  ): Future[Seq[ClaimValidationStatus]] = {
    val now                   = Instant.now()
    val expiryThreshold       = now.minus(ExpiryDays, ChronoUnit.DAYS)
    val (expired, nonExpired) = uploads.partition(isExpired(_, expiryThreshold))

    if (expired.isEmpty) {
      Future.successful(uploads)
    } else {
      val deleteFutures = expired.map { status =>
        logger.info(s"Purging expired upload: claimId=$claimId, reference=${status.reference}")
        repository.deleteByReference(claimId, status.reference)
      }
      Future.sequence(deleteFutures).map(_ => nonExpired)
    }
  }

  private def isExpired(status: ClaimValidationStatus, threshold: Instant): Boolean =
    status match {
      case s: AwaitingUploadStatus => s.initiateTimestamp.isBefore(threshold)
      case _                       => false
    }
}
