/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.mongodb.scala.MongoException
import org.scalatest.matchers.must.Matchers.*
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.CREATED
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import uk.gov.hmrc.charitiesclaimsvalidation.models.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.requests.UploadRequest
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.{ExpiredClaimReference, NotFoundClaimReference, SuccessResponse, UploadResultResponse}
import uk.gov.hmrc.charitiesclaimsvalidation.repositories.ClaimValidationStatusRepository
import uk.gov.hmrc.charitiesclaimsvalidation.util.BaseSpec
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import eu.timepit.refined.types.string.NonEmptyString

import java.net.URI
import java.time.Instant
import scala.concurrent.Future

class ClaimReferenceServiceSpec extends BaseSpec with MockitoSugar {
  private val claimId         = "claim-123"
  private val reference       = "ref-456"
  private val someFields      = Some(Map("foo" -> "bar"))
  private val existingStatus  = FileStatus.AwaitingUpload
  private val requestedStatus = FileStatus.Verifying

  trait Fixture {
    implicit val ec: scala.concurrent.ExecutionContext                       = scala.concurrent.ExecutionContext.global
    val mockClaimValidationStatusRepository: ClaimValidationStatusRepository = mock[ClaimValidationStatusRepository]
    val claimReferenceService                                                = new ClaimReferenceService(mockClaimValidationStatusRepository)(ec)
    given headerCarrier: HeaderCarrier =
      HeaderCarrier(authorization = Option(Authorization("bearerToken"))).withExtraHeaders("Content-Type" -> "application/json")
  }

  "ClaimReferenceService" - {

    "deleteByReference" - {

      "return SuccessResponse as true when claimValidationStatus is deleted" in new Fixture {
        when(mockClaimValidationStatusRepository.deleteByReference("C1", "R1"))
          .thenReturn(Future.successful(true))

        val result = claimReferenceService.deleteByReference("C1", "R1").futureValue

        result mustBe Right(SuccessResponse(true))

      }

      "return SuccessResponse as false when claimValidationStatus is not found" in new Fixture {
        when(mockClaimValidationStatusRepository.deleteByReference("C1", "R1"))
          .thenReturn(Future.successful(false))

        val result = claimReferenceService.deleteByReference("C1", "R1").futureValue

        result mustBe Left(NotFoundClaimReference(claimId = "C1", referenceId = "R1"))

      }

    }

    "storeUploadStatus" - {

      "return SuccessResponse as true when claimValidationStatus is created" in new Fixture {

        private val timestamp = Instant.parse("2025-01-01T12:00:00Z")

        private val request = UploadRequest(
          reference = NonEmptyString.unsafeFrom("ref-uuid-001"),
          validationType = ValidationType.GiftAid,
          uploadUrl = URI.create("https://test-url-1"),
          initiateTimestamp = timestamp,
          fields = someFields
        )

        when(
          mockClaimValidationStatusRepository.insert(
            any[AwaitingUploadStatus]()
          )
        )
          .thenReturn(Future.successful(true))

        val result = claimReferenceService.storeUploadStatus("123", request)

        status(result) mustBe CREATED

      }
    }

    "updateStatusByReference" - {
      val timestamp = Instant.parse("2025-01-01T12:00:00Z")

      "return NotFoundClaimReference if claim id or claim reference does not exist" in new Fixture {
        when(mockClaimValidationStatusRepository.findByReference(claimId, reference)).thenReturn(Future.successful(None))
        private val result = claimReferenceService.updateStatusByReference(claimId, reference, existingStatus, requestedStatus)
        whenReady(result) { response =>
          response should matchPattern {
            case Left(error: NotFoundClaimReference)
                if error.claimId == claimId &&
                  error.referenceId == reference =>
          }
        }
        verify(mockClaimValidationStatusRepository, times(1)).findByReference(claimId, reference)
        verify(mockClaimValidationStatusRepository, never())
          .updateFileStatusIfCurrentStatusMatches(claimId, reference, existingStatus, requestedStatus)
      }

      "return response as true when file status is modified successfully" in new Fixture {
        val validationStatusAwaitingUpload = AwaitingUploadStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.OtherIncome,
          uploadUrl = "/uploadUrl",
          initiateTimestamp = timestamp,
          fields = someFields,
          createdAt = timestamp,
          updatedAt = timestamp
        )
        when(mockClaimValidationStatusRepository.findByReference(claimId, reference))
          .thenReturn(Future.successful(Some(validationStatusAwaitingUpload)))
        when(mockClaimValidationStatusRepository.updateFileStatusIfCurrentStatusMatches(claimId, reference, existingStatus, requestedStatus))
          .thenReturn(Future.successful(true))

        whenReady(
          claimReferenceService.updateStatusByReference(
            claimId,
            reference,
            existingStatus,
            requestedStatus
          )
        ) {
          case Right(response) =>
            response.success shouldBe true
          case Left(error) =>
            fail(s"Expected Right(SuccessResponse(true)), but got Left($error)")
        }
        verify(mockClaimValidationStatusRepository, times(1)).findByReference(claimId, reference)
        verify(mockClaimValidationStatusRepository, times(1))
          .updateFileStatusIfCurrentStatusMatches(claimId, reference, existingStatus, requestedStatus)
      }

      s"return response as true with no mongo update when file status is not ${FileStatus.AwaitingUpload.value}" in new Fixture {
        val validationStatusVerifying = VerifyingStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.OtherIncome,
          createdAt = timestamp,
          updatedAt = timestamp
        )
        when(mockClaimValidationStatusRepository.findByReference(claimId, reference)).thenReturn(Future.successful(Some(validationStatusVerifying)))
        whenReady(
          claimReferenceService.updateStatusByReference(
            claimId,
            reference,
            existingStatus,
            requestedStatus
          )
        ) {
          case Right(response) =>
            response.success shouldBe true
          case Left(error) =>
            fail(s"Expected Right(SuccessResponse(false)), but got Left($error)")
        }
        verify(mockClaimValidationStatusRepository, times(1)).findByReference(claimId, reference)
        verify(mockClaimValidationStatusRepository, never)
          .updateFileStatusIfCurrentStatusMatches(claimId, reference, existingStatus, requestedStatus)
      }

      "fail with the same exception when the repository update operation fails" in new Fixture {
        private val mongoEx = new MongoException("mongo delete failed")
        when(mockClaimValidationStatusRepository.findByReference(claimId, reference)).thenReturn(Future.failed(mongoEx))
        private val result = claimReferenceService.updateStatusByReference(claimId, reference, existingStatus, requestedStatus)
        whenReady(result.failed) { ex =>
          ex mustBe mongoEx
        }
        verify(mockClaimValidationStatusRepository, times(1))
          .findByReference(claimId, reference)
      }
    }

    "getUploadResult" - {
      val today            = Instant.now()
      val expiredTimestamp = Instant.now().minusSeconds(60 * 60 * 24 * 8) // 8 days ago

      "return NotFoundClaimReference when reference does not exist" in new Fixture {
        when(mockClaimValidationStatusRepository.findByReference(claimId, reference))
          .thenReturn(Future.successful(None))

        val result = claimReferenceService.getUploadResult(claimId, reference).futureValue

        result mustBe Left(NotFoundClaimReference(claimId, reference))
        verify(mockClaimValidationStatusRepository, times(1)).findByReference(claimId, reference)
      }

      "return UploadResultResponse for AWAITING_UPLOAD status that is not expired" in new Fixture {
        val awaitingUploadStatus = AwaitingUploadStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.GiftAid,
          uploadUrl = "https://upload-url.com",
          initiateTimestamp = today,
          fields = someFields,
          createdAt = today,
          updatedAt = today
        )

        when(mockClaimValidationStatusRepository.findByReference(claimId, reference))
          .thenReturn(Future.successful(Some(awaitingUploadStatus)))

        val result = claimReferenceService.getUploadResult(claimId, reference).futureValue

        result mustBe Right(
          UploadResultResponse(
            reference = reference,
            validationType = ValidationType.GiftAid,
            fileStatus = "AWAITING_UPLOAD",
            uploadUrl = Some("https://upload-url.com")
          )
        )
        verify(mockClaimValidationStatusRepository, never()).deleteByReference(claimId, reference)
      }

      "return ExpiredClaimReference and delete document for AWAITING_UPLOAD status that is expired (7+ days old)" in new Fixture {
        val expiredAwaitingUploadStatus = AwaitingUploadStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.GiftAid,
          uploadUrl = "https://upload-url.com",
          initiateTimestamp = expiredTimestamp,
          fields = someFields,
          createdAt = expiredTimestamp,
          updatedAt = expiredTimestamp
        )

        when(mockClaimValidationStatusRepository.findByReference(claimId, reference))
          .thenReturn(Future.successful(Some(expiredAwaitingUploadStatus)))
        when(mockClaimValidationStatusRepository.deleteByReference(claimId, reference))
          .thenReturn(Future.successful(true))

        val result = claimReferenceService.getUploadResult(claimId, reference).futureValue

        result mustBe Left(ExpiredClaimReference(claimId, reference))
        verify(mockClaimValidationStatusRepository, times(1)).deleteByReference(claimId, reference)
      }

      "return ExpiredClaimReference even when deletion fails (concurrent delete scenario)" in new Fixture {
        val expiredAwaitingUploadStatus = AwaitingUploadStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.GiftAid,
          uploadUrl = "https://upload-url.com",
          initiateTimestamp = expiredTimestamp,
          fields = someFields,
          createdAt = expiredTimestamp,
          updatedAt = expiredTimestamp
        )

        when(mockClaimValidationStatusRepository.findByReference(claimId, reference))
          .thenReturn(Future.successful(Some(expiredAwaitingUploadStatus)))
        when(mockClaimValidationStatusRepository.deleteByReference(claimId, reference))
          .thenReturn(Future.successful(false)) // Deletion returns false (already deleted by concurrent request)

        val result = claimReferenceService.getUploadResult(claimId, reference).futureValue

        result mustBe Left(ExpiredClaimReference(claimId, reference))
        verify(mockClaimValidationStatusRepository, times(1)).deleteByReference(claimId, reference)
      }

      "return UploadResultResponse for VERIFYING status" in new Fixture {
        val verifyingStatus = VerifyingStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.OtherIncome,
          createdAt = today,
          updatedAt = today
        )

        when(mockClaimValidationStatusRepository.findByReference(claimId, reference))
          .thenReturn(Future.successful(Some(verifyingStatus)))

        val result = claimReferenceService.getUploadResult(claimId, reference).futureValue

        result mustBe Right(
          UploadResultResponse(
            reference = reference,
            validationType = ValidationType.OtherIncome,
            fileStatus = "VERIFYING"
          )
        )
      }

      "return UploadResultResponse for VERIFICATION_FAILED status with failure details" in new Fixture {
        val verificationFailedStatus = VerificationFailedStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.GiftAid,
          failureDetails = FailureDetails(FailureReason.Quarantine, "File has a virus"),
          createdAt = today,
          updatedAt = today
        )

        when(mockClaimValidationStatusRepository.findByReference(claimId, reference))
          .thenReturn(Future.successful(Some(verificationFailedStatus)))

        val result = claimReferenceService.getUploadResult(claimId, reference).futureValue

        result match {
          case Right(response) =>
            response.reference mustBe reference
            response.fileStatus mustBe "VERIFICATION_FAILED"
            response.failureDetails mustBe Some(FailureDetails(FailureReason.Quarantine, "File has a virus"))
          case Left(error) =>
            fail(s"Expected Right but got Left($error)")
        }
      }

      "return UploadResultResponse for VALIDATING status" in new Fixture {
        val validatingStatus = ValidatingStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.CommunityBuildings,
          createdAt = today,
          updatedAt = today
        )

        when(mockClaimValidationStatusRepository.findByReference(claimId, reference))
          .thenReturn(Future.successful(Some(validatingStatus)))

        val result = claimReferenceService.getUploadResult(claimId, reference).futureValue

        result mustBe Right(
          UploadResultResponse(
            reference = reference,
            validationType = ValidationType.CommunityBuildings,
            fileStatus = "VALIDATING"
          )
        )
      }

      "return UploadResultResponse for VALIDATED status with gift aid schedule data" in new Fixture {
        val giftAidData = GiftAidScheduleData(
          earliestDonationDate = Some(java.time.LocalDate.of(2025, 1, 31)),
          prevOverclaimedGiftAid = Some(BigDecimal(0)),
          totalDonations = Some(BigDecimal(1450)),
          donations = Nil
        )

        val validatedStatus = ValidatedStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.GiftAid,
          giftAidScheduleData = Some(giftAidData),
          createdAt = today,
          updatedAt = today
        )

        when(mockClaimValidationStatusRepository.findByReference(claimId, reference))
          .thenReturn(Future.successful(Some(validatedStatus)))

        val result = claimReferenceService.getUploadResult(claimId, reference).futureValue

        result match {
          case Right(response) =>
            response.reference mustBe reference
            response.fileStatus mustBe "VALIDATED"
            response.giftAidScheduleData mustBe Some(giftAidData)
          case Left(error) =>
            fail(s"Expected Right but got Left($error)")
        }
      }

      "return UploadResultResponse for VALIDATION_FAILED status with errors" in new Fixture {
        val errors = Seq(
          ValidationError("earliestDonationDate", "ERROR: Earliest donation date is missing."),
          ValidationError("donations[0]", "ERROR: Item You have entered data in an invalid area of the form.")
        )

        val validationFailedStatus = ValidationFailedStatus(
          claimId = claimId,
          reference = reference,
          validationType = ValidationType.GiftAid,
          giftAidScheduleData = Some(GiftAidScheduleData(None, None, None, Nil)),
          errors = errors,
          createdAt = today,
          updatedAt = today
        )

        when(mockClaimValidationStatusRepository.findByReference(claimId, reference))
          .thenReturn(Future.successful(Some(validationFailedStatus)))

        val result = claimReferenceService.getUploadResult(claimId, reference).futureValue

        result match {
          case Right(response) =>
            response.reference mustBe reference
            response.fileStatus mustBe "VALIDATION_FAILED"
            response.errors mustBe Some(errors)
          case Left(error) =>
            fail(s"Expected Right but got Left($error)")
        }
      }

      "fail with the same exception when the repository find operation fails" in new Fixture {
        private val mongoEx = new MongoException("mongo find failed")
        when(mockClaimValidationStatusRepository.findByReference(claimId, reference))
          .thenReturn(Future.failed(mongoEx))

        val result = claimReferenceService.getUploadResult(claimId, reference)

        whenReady(result.failed) { ex =>
          ex mustBe mongoEx
        }
      }
    }
  }
}
