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

import org.mockito.Mockito.*
import org.mongodb.scala.MongoException
import org.scalatest.matchers.must.Matchers.*
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.charitiesclaimsvalidation.controllers.actions.FakeClaimsAuthorisedAction
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{FileStatus, ValidationType}
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.{ExpiredClaimReference, NotFoundClaimReference, SuccessResponse, UploadResultResponse}
import uk.gov.hmrc.charitiesclaimsvalidation.services.ClaimReferenceService
import uk.gov.hmrc.charitiesclaimsvalidation.util.BaseSpec

import scala.concurrent.Future

class ClaimReferenceControllerSpec extends BaseSpec with MockitoSugar {

  trait Fixture {
    implicit val ec: scala.concurrent.ExecutionContext   = scala.concurrent.ExecutionContext.global
    private val cc                                       = Helpers.stubControllerComponents()
    private val fakeAuth                                 = new FakeClaimsAuthorisedAction(cc)
    val mockClaimReferenceService: ClaimReferenceService = mock[ClaimReferenceService]
    val controller                                       = new ClaimReferenceController(fakeAuth, mockClaimReferenceService, cc)
  }
  "ClaimReferenceController" - {

    "deleteByReference" - {

      "return 200 OK with success = true when claimValidationStatus is deleted" in new Fixture {

        when(mockClaimReferenceService.deleteByReference("C1", "R1"))
          .thenReturn(Future.successful(Right(SuccessResponse(true))))

        val result = controller.deleteByReference("C1", "R1")(FakeRequest())

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(SuccessResponse(true))
      }

      "return NOT FOUND with success = false when claimValidationStatus is not found" in new Fixture {

        when(mockClaimReferenceService.deleteByReference("C1", "R1"))
          .thenReturn(Future.successful(Left(NotFoundClaimReference("C1", "R1"))))

        val result = controller.deleteByReference("C1", "R1")(FakeRequest())

        status(result) mustBe NOT_FOUND
        contentAsJson(result) mustBe Json.obj(
          "error"   -> "CLAIM_REFERENCE_DOES_NOT_EXIST",
          "message" -> "There is no reference=R1 found for the given claimId=C1",
          "success" -> false
        )
      }
    }

    "updateFileStatus" - {
      val claimId             = "claim-123"
      val reference           = "ref-456"
      val successResponseTrue = SuccessResponse(true)

      def validRequest(status: FileStatus) = FakeRequest(PUT, "/")
        .withBody(
          Json.obj(
            "fileStatus" -> status.value
          )
        )
        .withHeaders(CONTENT_TYPE -> "application/json")

      "return 400 Bad Request when request JSON is invalid " in new Fixture {
        private val request = FakeRequest(PUT, "/").withBody(Json.obj("invalid" -> "json")).withHeaders(CONTENT_TYPE -> "application/json")

        private val result = controller.updateFileStatus(claimId, reference)(request)

        status(result) shouldBe BAD_REQUEST
        (contentAsJson(result) \ "error").as[String] mustBe "INVALID_UPDATE_FILE_STATUS_REQUEST"
        verify(mockClaimReferenceService, never()).updateStatusByReference(claimId, reference, FileStatus.AwaitingUpload, FileStatus.Verifying)
      }

      s"return 400 Bad Request when file status is not ${FileStatus.Verifying.value} in the request" in new Fixture {
        private val result = controller.updateFileStatus(claimId, reference)(validRequest(FileStatus.AwaitingUpload))

        status(result) shouldBe BAD_REQUEST
        (contentAsJson(result) \ "success").asOpt[Boolean].value mustBe false
        (contentAsJson(result) \ "error").as[String] mustBe "INVALID_FILE_STATUS"
        verify(mockClaimReferenceService, never()).updateStatusByReference(claimId, reference, FileStatus.AwaitingUpload, FileStatus.Verifying)
      }

      s"return 200 OK with success when the status is updated successfully from existing status ${FileStatus.AwaitingUpload.value} to " +
        s"new status ${FileStatus.Verifying.value} " in new Fixture {
          when(mockClaimReferenceService.updateStatusByReference(claimId, reference, FileStatus.AwaitingUpload, FileStatus.Verifying))
            .thenReturn(Future.successful(Right(successResponseTrue)))
          private val result = controller.updateFileStatus(claimId, reference)(validRequest(FileStatus.Verifying))

          status(result) shouldBe OK
          contentAsJson(result) mustBe Json.toJson(successResponseTrue)
          verify(mockClaimReferenceService, times(1)).updateStatusByReference(claimId, reference, FileStatus.AwaitingUpload, FileStatus.Verifying)
        }

      "throw error when mongo error thrown" in new Fixture {
        val mongoEx = new MongoException("mongo update failed")
        when(mockClaimReferenceService.updateStatusByReference(claimId, reference, FileStatus.AwaitingUpload, FileStatus.Verifying))
          .thenReturn(Future.failed(mongoEx))

        private val result = controller.updateFileStatus(claimId, reference)(validRequest(FileStatus.Verifying))

        whenReady(result.failed) { ex =>
          ex shouldBe a[MongoException]
          ex.getMessage should include("mongo update failed")
        }
        verify(mockClaimReferenceService, times(1)).updateStatusByReference(claimId, reference, FileStatus.AwaitingUpload, FileStatus.Verifying)
      }
    }

    "getUploadResult" - {
      val claimId   = "claim-123"
      val reference = "ref-456"

      "return 200 OK with upload result when found" in new Fixture {
        val uploadResult = UploadResultResponse(
          reference = reference,
          validationType = ValidationType.GiftAid,
          fileStatus = "AWAITING_UPLOAD",
          uploadUrl = Some("https://upload-url.com")
        )

        when(mockClaimReferenceService.getUploadResult(claimId, reference))
          .thenReturn(Future.successful(Right(uploadResult)))

        val result = controller.getUploadResult(claimId, reference)(FakeRequest())

        status(result) mustBe OK
        (contentAsJson(result) \ "reference").as[String] mustBe reference
        (contentAsJson(result) \ "validationType").as[String] mustBe "GiftAid"
        (contentAsJson(result) \ "fileStatus").as[String] mustBe "AWAITING_UPLOAD"
        (contentAsJson(result) \ "uploadUrl").as[String] mustBe "https://upload-url.com"
      }

      "return 404 NOT FOUND when reference does not exist" in new Fixture {
        when(mockClaimReferenceService.getUploadResult(claimId, reference))
          .thenReturn(Future.successful(Left(NotFoundClaimReference(claimId, reference))))

        val result = controller.getUploadResult(claimId, reference)(FakeRequest())

        status(result) mustBe NOT_FOUND
        (contentAsJson(result) \ "error").as[String] mustBe "CLAIM_REFERENCE_DOES_NOT_EXIST"
        (contentAsJson(result) \ "message").as[String] mustBe s"There is no reference=$reference found for the given claimId=$claimId"
      }

      "return 404 NOT FOUND when reference has expired" in new Fixture {
        when(mockClaimReferenceService.getUploadResult(claimId, reference))
          .thenReturn(Future.successful(Left(ExpiredClaimReference(claimId, reference))))

        val result = controller.getUploadResult(claimId, reference)(FakeRequest())

        status(result) mustBe NOT_FOUND
        (contentAsJson(result) \ "error").as[String] mustBe "CLAIM_REFERENCE_HAS_EXPIRED"
        (contentAsJson(result) \ "message").as[String] mustBe s"The requested reference=$reference for claim claimId=$claimId has expired"
      }

      "return 500 INTERNAL SERVER ERROR when service throws an exception" in new Fixture {
        val mongoEx = new MongoException("mongo error")
        when(mockClaimReferenceService.getUploadResult(claimId, reference))
          .thenReturn(Future.failed(mongoEx))

        val result = controller.getUploadResult(claimId, reference)(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        (contentAsJson(result) \ "error").as[String] mustBe "INTERNAL_SERVER_ERROR"
        (contentAsJson(result) \ "message").as[String] mustBe "Currently experiencing issues with our service"
      }

      "return upload result with VERIFYING status" in new Fixture {
        val uploadResult = UploadResultResponse(
          reference = reference,
          validationType = ValidationType.GiftAid,
          fileStatus = "VERIFYING"
        )

        when(mockClaimReferenceService.getUploadResult(claimId, reference))
          .thenReturn(Future.successful(Right(uploadResult)))

        val result = controller.getUploadResult(claimId, reference)(FakeRequest())

        status(result) mustBe OK
        (contentAsJson(result) \ "fileStatus").as[String] mustBe "VERIFYING"
        (contentAsJson(result) \ "uploadUrl").asOpt[String] mustBe None
      }

      "return upload result with VALIDATING status" in new Fixture {
        val uploadResult = UploadResultResponse(
          reference = reference,
          validationType = ValidationType.OtherIncome,
          fileStatus = "VALIDATING"
        )

        when(mockClaimReferenceService.getUploadResult(claimId, reference))
          .thenReturn(Future.successful(Right(uploadResult)))

        val result = controller.getUploadResult(claimId, reference)(FakeRequest())

        status(result) mustBe OK
        (contentAsJson(result) \ "fileStatus").as[String] mustBe "VALIDATING"
        (contentAsJson(result) \ "validationType").as[String] mustBe "OtherIncome"
      }
    }
  }
}
