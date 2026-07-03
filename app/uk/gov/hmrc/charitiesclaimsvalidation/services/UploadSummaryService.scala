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
