/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services

import eu.timepit.refined.types.all.NonEmptyString
import org.mockito.ArgumentMatchers.{any, eq as mEq}
import org.mockito.Mockito.{never, times, verify, when}
import org.mongodb.scala.MongoException
import org.scalatest.matchers.must.Matchers.*
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{AwaitingUploadStatus, ValidationType, VerifyingStatus}
import uk.gov.hmrc.charitiesclaimsvalidation.models.requests.UploadRequest
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.SuccessResponse
import uk.gov.hmrc.charitiesclaimsvalidation.repositories.ClaimValidationStatusRepository
import uk.gov.hmrc.charitiesclaimsvalidation.util.BaseSpec

import java.net.URI
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ClaimServiceSpec extends BaseSpec with MockitoSugar {
  given ExecutionContext = global
  private val claimId    = "12345"

  trait Fixture {
    val mockRepository: ClaimValidationStatusRepository = mock[ClaimValidationStatusRepository]
    val service                                         = new ClaimService(mockRepository)

    val someFields: Option[Map[String, String]] = Some(Map("foo" -> "bar"))

    val uploadRequest = UploadRequest(
      reference = NonEmptyString.unsafeFrom("ref-uuid-001"),
      validationType = ValidationType.GiftAid,
      uploadUrl = URI.create("https://test-url-1"),
      initiateTimestamp = Instant.now,
      fields = someFields
    )

    val uploadUrl = "http://upload-url"

  }

  "ClaimService " - {

    "deleteClaims" - {
      "complete successfully with SuccessResponse as true when the repository deletes all the records for a given claim Id" in new Fixture {
        when(mockRepository.deleteByClaimId(mEq(claimId))).thenReturn(Future.successful(true))

        whenReady(service.deleteClaims(claimId)) { result =>
          result mustBe SuccessResponse(true)
        }
        verify(mockRepository, times(1)).deleteByClaimId(mEq(claimId))
      }

      "fail with the same exception when the repository delete operation fails" in new Fixture {
        val mongoEx = new MongoException("mongo delete failed")
        when(mockRepository.deleteByClaimId(mEq(claimId))).thenReturn(Future.failed(mongoEx))

        whenReady(service.deleteClaims(claimId).failed) { ex =>
          ex mustBe mongoEx
        }
        verify(mockRepository, times(1)).deleteByClaimId(mEq(claimId))
      }
    }

    "storeUploadStatus" - {

      "return 201 Created with {success:true} when an AwaitingUploadStatus already exists (same reference + validationType)" in new Fixture {
        val existing = AwaitingUploadStatus(
          claimId,
          reference = uploadRequest.reference.value,
          validationType = uploadRequest.validationType,
          uploadUrl = uploadUrl,
          createdAt = Instant.now(),
          updatedAt = Instant.now(),
          initiateTimestamp = Instant.now(),
          fields = someFields
        )

        when(mockRepository.findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType)))
          .thenReturn(Future.successful(Some(existing)))

        val result = service.storeUploadStatus(claimId, uploadRequest)
        status(result) mustBe CREATED
        contentAsJson(result) mustBe Json.obj("success" -> true)

        verify(mockRepository, times(1)).findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType))
        verify(mockRepository, never()).deleteByReference(any[String], any[String])
        verify(mockRepository, never()).insert(any[AwaitingUploadStatus])
        verify(mockRepository, never()).update(any[String], any[String], any[AwaitingUploadStatus])
      }

      "return 201 Created with {success:true} when an AwaitingUploadStatus exists (same validationType, different reference) and delete+insert succeeds" in new Fixture {
        val existingDifferentRef = AwaitingUploadStatus(
          claimId,
          reference = "old-ref-999",
          validationType = uploadRequest.validationType,
          uploadUrl = uploadUrl,
          createdAt = Instant.now(),
          updatedAt = Instant.now(),
          initiateTimestamp = Instant.now(),
          fields = someFields
        )

        when(mockRepository.findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType)))
          .thenReturn(Future.successful(Some(existingDifferentRef)))

        when(mockRepository.deleteByReference(mEq(claimId), mEq(existingDifferentRef.reference)))
          .thenReturn(Future.successful(true))

        when(mockRepository.insert(any[AwaitingUploadStatus]))
          .thenReturn(Future.successful(true))

        val result = service.storeUploadStatus(claimId, uploadRequest)
        status(result) mustBe CREATED
        contentAsJson(result) mustBe Json.obj("success" -> true)

        verify(mockRepository, times(1)).findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType))
        verify(mockRepository, times(1)).deleteByReference(mEq(claimId), mEq(existingDifferentRef.reference))
        verify(mockRepository, times(1)).insert(any[AwaitingUploadStatus])
        verify(mockRepository, never()).update(any[String], any[String], any[AwaitingUploadStatus])
      }

      "return 500 InternalServerError with {success:false} when an AwaitingUploadStatus exists (same validationType, different reference), delete succeeds and insert returns false" in new Fixture {
        val existingDifferentRef = AwaitingUploadStatus(
          claimId,
          reference = "old-ref-999",
          validationType = uploadRequest.validationType,
          uploadUrl = uploadUrl,
          createdAt = Instant.now(),
          updatedAt = Instant.now(),
          initiateTimestamp = Instant.now(),
          fields = someFields
        )

        when(mockRepository.findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType)))
          .thenReturn(Future.successful(Some(existingDifferentRef)))

        when(mockRepository.deleteByReference(mEq(claimId), mEq(existingDifferentRef.reference)))
          .thenReturn(Future.successful(true))

        when(mockRepository.insert(any[AwaitingUploadStatus]))
          .thenReturn(Future.successful(false))

        val result = service.storeUploadStatus(claimId, uploadRequest)
        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("success" -> false)

        verify(mockRepository, times(1)).findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType))
        verify(mockRepository, times(1)).deleteByReference(mEq(claimId), mEq(existingDifferentRef.reference))
        verify(mockRepository, times(1)).insert(any[AwaitingUploadStatus])
        verify(mockRepository, never()).update(any[String], any[String], any[AwaitingUploadStatus])
      }

      "return 201 Created with {success:true} when no status exists and insert succeeds" in new Fixture {
        when(mockRepository.findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType)))
          .thenReturn(Future.successful(None))

        when(mockRepository.insert(any[AwaitingUploadStatus]))
          .thenReturn(Future.successful(true))

        val result = service.storeUploadStatus(claimId, uploadRequest)
        status(result) mustBe CREATED
        contentAsJson(result) mustBe Json.obj("success" -> true)

        verify(mockRepository, times(1)).findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType))
        verify(mockRepository, times(1)).insert(any[AwaitingUploadStatus])
        verify(mockRepository, never()).deleteByReference(any[String], any[String])
        verify(mockRepository, never()).update(any[String], any[String], any[AwaitingUploadStatus])
      }

      "return 500 InternalServerError with {success:false} when no status exists and insert returns false" in new Fixture {
        when(mockRepository.findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType)))
          .thenReturn(Future.successful(None))

        when(mockRepository.insert(any[AwaitingUploadStatus]))
          .thenReturn(Future.successful(false))

        val result = service.storeUploadStatus(claimId, uploadRequest)
        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("success" -> false)

        verify(mockRepository, times(1)).findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType))
        verify(mockRepository, times(1)).insert(any[AwaitingUploadStatus])
        verify(mockRepository, never()).deleteByReference(any[String], any[String])
        verify(mockRepository, never()).update(any[String], any[String], any[AwaitingUploadStatus])
      }

      "return 500 InternalServerError with {success:false} when repository returns a non-AwaitingUploadStatus" in new Fixture {
        val nonAwaiting = VerifyingStatus(
          claimId = claimId,
          reference = uploadRequest.reference.value,
          validationType = uploadRequest.validationType,
          createdAt = Instant.now(),
          updatedAt = Instant.now()
        )

        when(mockRepository.findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType)))
          .thenReturn(Future.successful(Some(nonAwaiting)))

        val result = service.storeUploadStatus(claimId, uploadRequest)
        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj("success" -> false)

        verify(mockRepository, times(1)).findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType))
        verify(mockRepository, never()).deleteByReference(any[String], any[String])
        verify(mockRepository, never()).insert(any[AwaitingUploadStatus])
        verify(mockRepository, never()).update(any[String], any[String], any[AwaitingUploadStatus])
      }

      "fail with the same exception when findByClaimIdAndValidationType fails" in new Fixture {
        val mongoEx = new MongoException("mongo find failed")

        when(mockRepository.findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType)))
          .thenReturn(Future.failed(mongoEx))

        whenReady(service.storeUploadStatus(claimId, uploadRequest).failed) { ex =>
          ex mustBe mongoEx
        }

        verify(mockRepository, times(1)).findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType))
        verify(mockRepository, never()).deleteByReference(any[String], any[String])
        verify(mockRepository, never()).insert(any[AwaitingUploadStatus])
        verify(mockRepository, never()).update(any[String], any[String], any[AwaitingUploadStatus])
      }

      "fail with the same exception when deleteByReference fails (existing AwaitingUploadStatus with different reference)" in new Fixture {
        val mongoEx = new MongoException("mongo delete failed")

        val existingDifferentRef = AwaitingUploadStatus(
          claimId,
          reference = "old-ref-999",
          validationType = uploadRequest.validationType,
          uploadUrl = uploadUrl,
          createdAt = Instant.now(),
          updatedAt = Instant.now(),
          initiateTimestamp = Instant.now(),
          fields = someFields
        )

        when(mockRepository.findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType)))
          .thenReturn(Future.successful(Some(existingDifferentRef)))

        when(mockRepository.deleteByReference(mEq(claimId), mEq(existingDifferentRef.reference)))
          .thenReturn(Future.failed(mongoEx))

        whenReady(service.storeUploadStatus(claimId, uploadRequest).failed) { ex =>
          ex mustBe mongoEx
        }

        verify(mockRepository, times(1)).findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType))
        verify(mockRepository, times(1)).deleteByReference(mEq(claimId), mEq(existingDifferentRef.reference))
        verify(mockRepository, never()).insert(any[AwaitingUploadStatus])
        verify(mockRepository, never()).update(any[String], any[String], any[AwaitingUploadStatus])
      }

      "fail with the same exception when insert fails (no status exists)" in new Fixture {
        val mongoEx = new MongoException("mongo insert failed")

        when(mockRepository.findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType)))
          .thenReturn(Future.successful(None))

        when(mockRepository.insert(any[AwaitingUploadStatus]))
          .thenReturn(Future.failed(mongoEx))

        whenReady(service.storeUploadStatus(claimId, uploadRequest).failed) { ex =>
          ex mustBe mongoEx
        }

        verify(mockRepository, times(1)).findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType))
        verify(mockRepository, times(1)).insert(any[AwaitingUploadStatus])
        verify(mockRepository, never()).deleteByReference(any[String], any[String])
        verify(mockRepository, never()).update(any[String], any[String], any[AwaitingUploadStatus])
      }

      "fail with the same exception when insert fails after deleteByReference succeeds (existing AwaitingUploadStatus with different reference)" in new Fixture {
        val mongoEx = new MongoException("mongo insert failed")

        val existingDifferentRef = AwaitingUploadStatus(
          claimId,
          reference = "old-ref-999",
          validationType = uploadRequest.validationType,
          uploadUrl = uploadUrl,
          createdAt = Instant.now(),
          updatedAt = Instant.now(),
          initiateTimestamp = Instant.now(),
          fields = someFields
        )

        when(mockRepository.findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType)))
          .thenReturn(Future.successful(Some(existingDifferentRef)))

        when(mockRepository.deleteByReference(mEq(claimId), mEq(existingDifferentRef.reference)))
          .thenReturn(Future.successful(true))

        when(mockRepository.insert(any[AwaitingUploadStatus]))
          .thenReturn(Future.failed(mongoEx))

        whenReady(service.storeUploadStatus(claimId, uploadRequest).failed) { ex =>
          ex mustBe mongoEx
        }

        verify(mockRepository, times(1)).findByClaimIdAndValidationType(mEq(claimId), mEq(uploadRequest.validationType))
        verify(mockRepository, times(1)).deleteByReference(mEq(claimId), mEq(existingDifferentRef.reference))
        verify(mockRepository, times(1)).insert(any[AwaitingUploadStatus])
        verify(mockRepository, never()).update(any[String], any[String], any[AwaitingUploadStatus])
      }
    }

    "touchTtlByClaimId" - {

      "complete successfully when repository call succeeds" in new Fixture {
        when(mockRepository.touchTtlByClaimId(mEq(claimId)))
          .thenReturn(Future.unit)

        service.touchTtlByClaimId(claimId).futureValue mustBe ()

        verify(mockRepository).touchTtlByClaimId(mEq(claimId))
      }
    }
  }
}
