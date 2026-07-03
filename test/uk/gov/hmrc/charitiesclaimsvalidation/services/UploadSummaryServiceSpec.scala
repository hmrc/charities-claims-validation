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

import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.{UploadSummaryItem, UploadSummaryResponse}
import uk.gov.hmrc.charitiesclaimsvalidation.repositories.ClaimValidationStatusRepository
import uk.gov.hmrc.charitiesclaimsvalidation.util.BaseSpec

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

class UploadSummaryServiceSpec extends BaseSpec with MockitoSugar {

  private val claimId    = "test-claim-123"
  private val reference  = "ref-uuid-001"
  private val uploadUrl  = "https://upscan-upload-proxy/bucket"
  private val now        = Instant.now()
  private val someFields = Some(Map("foo" -> "bar"))

  "UploadSummaryService" - {

    "getUploadSummary" - {

      "return None when no uploads exist for the claim" in {
        val repository = mock[ClaimValidationStatusRepository]
        val service    = new UploadSummaryService(repository)

        when(repository.getClaimSummary(claimId)).thenReturn(Future.successful(None))

        val result = service.getUploadSummary(claimId).futureValue

        result shouldBe None
      }

      "return summary with single upload" in {
        val repository = mock[ClaimValidationStatusRepository]
        val service    = new UploadSummaryService(repository)

        val status = VerifyingStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.GiftAid,
          createdAt = now,
          updatedAt = now
        )

        when(repository.getClaimSummary(claimId)).thenReturn(
          Future.successful(Some(ClaimValidationSummary(claimId, Seq(status))))
        )

        val result = service.getUploadSummary(claimId).futureValue

        result shouldBe defined
        result.get.uploads should have size 1
        result.get.uploads.head shouldBe UploadSummaryItem(
          reference = reference,
          validationType = ValidationType.GiftAid,
          fileStatus = "VERIFYING",
          uploadUrl = None
        )
      }

      "return summary with multiple uploads" in {
        val repository = mock[ClaimValidationStatusRepository]
        val service    = new UploadSummaryService(repository)

        val status1 = VerifyingStatus(
          claimId = claimId,
          reference = "ref-1",
          validationType = ValidationType.GiftAid,
          createdAt = now,
          updatedAt = now
        )
        val status2 = ValidatedStatus(
          claimId = claimId,
          reference = "ref-2",
          validationType = ValidationType.OtherIncome,
          createdAt = now,
          updatedAt = now
        )

        when(repository.getClaimSummary(claimId)).thenReturn(
          Future.successful(Some(ClaimValidationSummary(claimId, Seq(status1, status2))))
        )

        val result = service.getUploadSummary(claimId).futureValue

        result shouldBe defined
        result.get.uploads should have size 2
        result.get.uploads.map(_.fileStatus) should contain allOf ("VERIFYING", "VALIDATED")
      }

      "include uploadUrl when status is AWAITING_UPLOAD" in {
        val repository = mock[ClaimValidationStatusRepository]
        val service    = new UploadSummaryService(repository)

        val status = AwaitingUploadStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.GiftAid,
          uploadUrl = uploadUrl,
          initiateTimestamp = now,
          fields = someFields,
          createdAt = now,
          updatedAt = now
        )

        when(repository.getClaimSummary(claimId)).thenReturn(
          Future.successful(Some(ClaimValidationSummary(claimId, Seq(status))))
        )

        val result = service.getUploadSummary(claimId).futureValue

        result shouldBe defined
        result.get.uploads.head.uploadUrl shouldBe Some(uploadUrl)
        result.get.uploads.head.fileStatus shouldBe "AWAITING_UPLOAD"
      }

      "purge expired AWAITING_UPLOAD uploads (7+ days old)" in {
        val repository = mock[ClaimValidationStatusRepository]
        val service    = new UploadSummaryService(repository)

        val expiredTime = now.minus(8, ChronoUnit.DAYS)
        val expiredStatus = AwaitingUploadStatus(
          claimId = claimId,
          reference = "expired-ref",
          validationType = ValidationType.GiftAid,
          uploadUrl = uploadUrl,
          initiateTimestamp = expiredTime,
          fields = someFields,
          createdAt = now,
          updatedAt = now
        )
        val validStatus = VerifyingStatus(
          claimId = claimId,
          reference = "valid-ref",
          validationType = ValidationType.OtherIncome,
          createdAt = now,
          updatedAt = now
        )

        when(repository.getClaimSummary(claimId)).thenReturn(
          Future.successful(Some(ClaimValidationSummary(claimId, Seq(expiredStatus, validStatus))))
        )
        when(repository.deleteByReference(claimId, "expired-ref")).thenReturn(Future.successful(true))

        val result = service.getUploadSummary(claimId).futureValue

        result shouldBe defined
        result.get.uploads should have size 1
        result.get.uploads.head.reference shouldBe "valid-ref"
        verify(repository).deleteByReference(claimId, "expired-ref")
      }

      "not purge AWAITING_UPLOAD uploads less than 7 days old" in {
        val repository = mock[ClaimValidationStatusRepository]
        val service    = new UploadSummaryService(repository)

        val almostExpiredTime = now.minus(6, ChronoUnit.DAYS)
        val status = AwaitingUploadStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.GiftAid,
          uploadUrl = uploadUrl,
          initiateTimestamp = almostExpiredTime,
          fields = someFields,
          createdAt = almostExpiredTime,
          updatedAt = almostExpiredTime
        )

        when(repository.getClaimSummary(claimId)).thenReturn(
          Future.successful(Some(ClaimValidationSummary(claimId, Seq(status))))
        )

        val result = service.getUploadSummary(claimId).futureValue

        result shouldBe defined
        result.get.uploads should have size 1
        verify(repository, never()).deleteByReference(claimId, reference)
      }

      "not purge non-AWAITING_UPLOAD statuses even if old" in {
        val repository = mock[ClaimValidationStatusRepository]
        val service    = new UploadSummaryService(repository)

        val oldTime = now.minus(30, ChronoUnit.DAYS)
        val status = VerifyingStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.GiftAid,
          createdAt = oldTime,
          updatedAt = oldTime
        )

        when(repository.getClaimSummary(claimId)).thenReturn(
          Future.successful(Some(ClaimValidationSummary(claimId, Seq(status))))
        )

        val result = service.getUploadSummary(claimId).futureValue

        result shouldBe defined
        result.get.uploads should have size 1
        verify(repository, never()).deleteByReference(claimId, reference)
      }

      "return None when all uploads are expired and purged" in {
        val repository = mock[ClaimValidationStatusRepository]
        val service    = new UploadSummaryService(repository)

        val expiredTime = now.minus(8, ChronoUnit.DAYS)
        val expiredStatus = AwaitingUploadStatus(
          claimId = claimId,
          reference = "expired-ref",
          validationType = ValidationType.GiftAid,
          uploadUrl = uploadUrl,
          initiateTimestamp = expiredTime,
          fields = someFields,
          createdAt = expiredTime,
          updatedAt = expiredTime
        )

        when(repository.getClaimSummary(claimId)).thenReturn(
          Future.successful(Some(ClaimValidationSummary(claimId, Seq(expiredStatus))))
        )
        when(repository.deleteByReference(claimId, "expired-ref")).thenReturn(Future.successful(true))

        val result = service.getUploadSummary(claimId).futureValue

        result shouldBe None
        verify(repository).deleteByReference(claimId, "expired-ref")
      }

      "purge multiple expired uploads" in {
        val repository = mock[ClaimValidationStatusRepository]
        val service    = new UploadSummaryService(repository)

        val expiredTime = now.minus(8, ChronoUnit.DAYS)
        val expired1 = AwaitingUploadStatus(
          claimId = claimId,
          reference = "expired-1",
          validationType = ValidationType.GiftAid,
          uploadUrl = uploadUrl,
          initiateTimestamp = expiredTime,
          fields = someFields,
          createdAt = expiredTime,
          updatedAt = expiredTime
        )
        val expired2 = AwaitingUploadStatus(
          claimId = claimId,
          reference = "expired-2",
          validationType = ValidationType.OtherIncome,
          uploadUrl = uploadUrl,
          initiateTimestamp = expiredTime,
          fields = someFields,
          createdAt = expiredTime,
          updatedAt = expiredTime
        )

        when(repository.getClaimSummary(claimId)).thenReturn(
          Future.successful(Some(ClaimValidationSummary(claimId, Seq(expired1, expired2))))
        )
        when(repository.deleteByReference(claimId, "expired-1")).thenReturn(Future.successful(true))
        when(repository.deleteByReference(claimId, "expired-2")).thenReturn(Future.successful(true))

        val result = service.getUploadSummary(claimId).futureValue

        result shouldBe None
        verify(repository).deleteByReference(claimId, "expired-1")
        verify(repository).deleteByReference(claimId, "expired-2")
      }
    }
  }
}
