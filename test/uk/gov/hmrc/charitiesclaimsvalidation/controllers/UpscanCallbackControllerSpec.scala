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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, matchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.charitiesclaimsvalidation.models.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.UpscanCallbackRequest
import uk.gov.hmrc.charitiesclaimsvalidation.services.{CallbackResult, UpscanCallbackService}
import java.time.Instant

import scala.concurrent.Future

class UpscanCallbackControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterAll {

  import org.apache.pekko.actor.ActorSystem
  import org.apache.pekko.stream.Materializer

  import scala.concurrent.ExecutionContext

  implicit val actorSystem: ActorSystem = ActorSystem("test")
  implicit val mat: Materializer        = Materializer(actorSystem)
  implicit val ec: ExecutionContext     = scala.concurrent.ExecutionContext.global
  private val claimId                   = "test-claim-123"
  private val reference                 = "11370e18-6e24-453e-b45a-76d3e32ea33d"
  private val createdAtTimestamp        = Instant.parse("2025-01-01T12:00:00Z")
  private val updatedAtTimestamp        = Instant.parse("2025-01-02T12:00:00Z")

  override protected def afterAll(): Unit =
    actorSystem.terminate(): Unit

  "UpscanCallbackController" should {

    "return NO_CONTENT when service returns Success" in {
      val mockService = mock[UpscanCallbackService]

      val validatingStatus = ValidatingStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        createdAt = createdAtTimestamp,
        updatedAt = updatedAtTimestamp
      )

      when(mockService.processCallback(eqTo(claimId), any[UpscanCallbackRequest]))
        .thenReturn(Future.successful(CallbackResult.Success, Some(validatingStatus)))

      when(mockService.processValidation(eqTo(claimId), any[UpscanCallbackRequest], Some(any[ClaimValidationStatus])))
        .thenReturn(Future.successful(()))

      val controller = new UpscanCallbackController(
        mockService,
        Helpers.stubControllerComponents()
      )

      val requestBody = Json.obj(
        "reference"   -> reference,
        "downloadUrl" -> "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
        "fileStatus"  -> "READY",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "test.ods",
          "fileMimeType"    -> "application/vnd.oasis.opendocument.spreadsheet",
          "uploadTimestamp" -> "2018-04-24T09:30:00Z",
          "checksum"        -> "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
          "size"            -> 987
        )
      )

      val fakeRequest = FakeRequest("POST", s"/$claimId/upscan-callback")
        .withBody(requestBody)
        .asInstanceOf[play.api.mvc.Request[JsValue]]

      val result: Future[Result] = controller.callback(claimId)(fakeRequest)

      status(result) shouldBe Status.NO_CONTENT
      verify(mockService).processCallback(eqTo(claimId), any[UpscanCallbackRequest])
    }

    "return NO_CONTENT when service returns StartValidation" in {
      val mockService = mock[UpscanCallbackService]

      val validatingStatus = ValidatingStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        createdAt = createdAtTimestamp,
        updatedAt = updatedAtTimestamp
      )

      when(mockService.processCallback(eqTo(claimId), any[UpscanCallbackRequest]))
        .thenReturn(Future.successful(CallbackResult.StartValidation, Some(validatingStatus)))

      when(mockService.processValidation(eqTo(claimId), any[UpscanCallbackRequest], Some(any[ClaimValidationStatus])))
        .thenReturn(Future.successful(()))

      val controller = new UpscanCallbackController(
        mockService,
        Helpers.stubControllerComponents()
      )

      val requestBody = Json.obj(
        "reference"   -> reference,
        "downloadUrl" -> "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
        "fileStatus"  -> "READY",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "test.ods",
          "fileMimeType"    -> "application/vnd.oasis.opendocument.spreadsheet",
          "uploadTimestamp" -> "2018-04-24T09:30:00Z",
          "checksum"        -> "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
          "size"            -> 987
        )
      )

      val fakeRequest = FakeRequest("POST", s"/$claimId/upscan-callback")
        .withBody(requestBody)
        .asInstanceOf[play.api.mvc.Request[JsValue]]

      val result: Future[Result] = controller.callback(claimId)(fakeRequest)

      status(result) shouldBe Status.NO_CONTENT
      verify(mockService).processCallback(eqTo(claimId), any[UpscanCallbackRequest])
    }

    "return NOT_FOUND when service returns NotFound" in {
      val mockService = mock[UpscanCallbackService]

      when(mockService.processCallback(eqTo(claimId), any[UpscanCallbackRequest]))
        .thenReturn(Future.successful(CallbackResult.NotFound, None))

      when(mockService.processValidation(eqTo(claimId), any[UpscanCallbackRequest], Some(any[ClaimValidationStatus])))
        .thenReturn(Future.successful(()))

      val controller = new UpscanCallbackController(
        mockService,
        Helpers.stubControllerComponents()
      )

      val requestBody = Json.obj(
        "reference"   -> reference,
        "fileStatus"  -> "READY",
        "downloadUrl" -> "https://test.com/file",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "test.ods",
          "fileMimeType"    -> "application/vnd.oasis.opendocument.spreadsheet",
          "uploadTimestamp" -> "2018-04-24T09:30:00Z",
          "checksum"        -> "abc123",
          "size"            -> 100
        )
      )

      val fakeRequest = FakeRequest("POST", s"/$claimId/upscan-callback")
        .withBody(requestBody)
        .asInstanceOf[play.api.mvc.Request[JsValue]]

      val result: Future[Result] = controller.callback(claimId)(fakeRequest)

      status(result) shouldBe Status.NOT_FOUND
      verify(mockService).processCallback(eqTo(claimId), any[UpscanCallbackRequest])
    }

    "return INTERNAL_SERVER_ERROR when service returns UpdateFailed" in {
      val mockService = mock[UpscanCallbackService]

      when(mockService.processCallback(eqTo(claimId), any[UpscanCallbackRequest]))
        .thenReturn(Future.successful((CallbackResult.UpdateFailed, None)))

      val controller = new UpscanCallbackController(
        mockService,
        Helpers.stubControllerComponents()
      )

      val requestBody = Json.obj(
        "reference"   -> reference,
        "fileStatus"  -> "READY",
        "downloadUrl" -> "https://test.com/file",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "test.ods",
          "fileMimeType"    -> "application/vnd.oasis.opendocument.spreadsheet",
          "uploadTimestamp" -> "2018-04-24T09:30:00Z",
          "checksum"        -> "abc123",
          "size"            -> 100
        )
      )

      val fakeRequest = FakeRequest("POST", s"/$claimId/upscan-callback")
        .withBody(requestBody)
        .asInstanceOf[play.api.mvc.Request[JsValue]]

      val result: Future[Result] = controller.callback(claimId)(fakeRequest)

      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      verify(mockService).processCallback(eqTo(claimId), any[UpscanCallbackRequest])
    }

    "return INTERNAL_SERVER_ERROR when service throws exception" in {
      val mockService = mock[UpscanCallbackService]

      when(mockService.processCallback(eqTo(claimId), any[UpscanCallbackRequest]))
        .thenReturn(Future.failed(new RuntimeException("Database error")))

      val controller = new UpscanCallbackController(
        mockService,
        Helpers.stubControllerComponents()
      )

      val requestBody = Json.obj(
        "reference"   -> reference,
        "fileStatus"  -> "READY",
        "downloadUrl" -> "https://test.com/file",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "test.ods",
          "fileMimeType"    -> "application/vnd.oasis.opendocument.spreadsheet",
          "uploadTimestamp" -> "2018-04-24T09:30:00Z",
          "checksum"        -> "abc123",
          "size"            -> 100
        )
      )

      val fakeRequest = FakeRequest("POST", s"/$claimId/upscan-callback")
        .withBody(requestBody)
        .asInstanceOf[play.api.mvc.Request[JsValue]]

      val result: Future[Result] = controller.callback(claimId)(fakeRequest)

      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      verify(mockService).processCallback(eqTo(claimId), any[UpscanCallbackRequest])
    }

    "return BAD_REQUEST when JSON is invalid" in {
      val mockService = mock[UpscanCallbackService]

      val controller = new UpscanCallbackController(mockService, Helpers.stubControllerComponents())

      val requestBody = Json.obj("invalid" -> "data")
      val fakeRequest = FakeRequest("POST", s"/$claimId/upscan-callback")
        .withBody(requestBody)
        .asInstanceOf[play.api.mvc.Request[JsValue]]

      val result: Future[Result] = controller.callback(claimId)(fakeRequest)

      status(result) shouldBe Status.BAD_REQUEST
      verify(mockService, never()).processCallback(any[String], any[UpscanCallbackRequest])
    }

    "return NO_CONTENT when MIME type is other than application/vnd.oasis.opendocument.spreadsheet" in {
      val mockService = mock[UpscanCallbackService]

      val verificationFailedStatus = VerificationFailedStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        failureDetails = FailureDetails(
          failureReason = FailureReason.Rejected,
          message = s"MIME type application/pdf is not allowed"
        ),
        createdAt = createdAtTimestamp,
        updatedAt = updatedAtTimestamp
      )

      when(mockService.processCallback(eqTo(claimId), any[UpscanCallbackRequest]))
        .thenReturn(Future.successful(CallbackResult.Success, Some(verificationFailedStatus)))

      val controller = new UpscanCallbackController(
        mockService,
        Helpers.stubControllerComponents()
      )

      val requestBody = Json.obj(
        "reference"   -> reference,
        "fileStatus"  -> "READY",
        "downloadUrl" -> "https://test.com/file",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "test.pdf",
          "fileMimeType"    -> "application/pdf",
          "uploadTimestamp" -> "2018-04-24T09:30:00Z",
          "checksum"        -> "abc123",
          "size"            -> 100
        )
      )

      val fakeRequest = FakeRequest("POST", s"/$claimId/upscan-callback")
        .withBody(requestBody)
        .asInstanceOf[play.api.mvc.Request[JsValue]]

      val result: Future[Result] = controller.callback(claimId)(fakeRequest)

      status(result) shouldBe Status.NO_CONTENT
      verify(mockService).processCallback(eqTo(claimId), any[UpscanCallbackRequest])
    }

    "return NO_CONTENT and recover when async processValidation fails" in {
      val mockService = mock[UpscanCallbackService]

      val validatingStatus = ValidatingStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        createdAt = createdAtTimestamp,
        updatedAt = updatedAtTimestamp
      )

      when(mockService.processCallback(eqTo(claimId), any[UpscanCallbackRequest]))
        .thenReturn(Future.successful(CallbackResult.StartValidation, Some(validatingStatus)))

      when(
        mockService.processValidation(
          eqTo(claimId),
          any[UpscanCallbackRequest],
          eqTo(Some(validatingStatus))
        )
      ).thenReturn(Future.failed(new RuntimeException("Validation failed")))

      val controller = new UpscanCallbackController(
        mockService,
        Helpers.stubControllerComponents()
      )

      val requestBody = Json.obj(
        "reference"   -> reference,
        "downloadUrl" -> "https://test.com/file",
        "fileStatus"  -> "READY",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "test.ods",
          "fileMimeType"    -> "application/vnd.oasis.opendocument.spreadsheet",
          "uploadTimestamp" -> "2018-04-24T09:30:00Z",
          "checksum"        -> "abc123",
          "size"            -> 100
        )
      )

      val fakeRequest = FakeRequest("POST", s"/$claimId/upscan-callback")
        .withBody(requestBody)
        .asInstanceOf[play.api.mvc.Request[JsValue]]

      val result = controller.callback(claimId)(fakeRequest)

      status(result) shouldBe Status.NO_CONTENT

      verify(mockService).processCallback(eqTo(claimId), any[UpscanCallbackRequest])
      verify(mockService).processValidation(
        eqTo(claimId),
        any[UpscanCallbackRequest],
        eqTo(Some(validatingStatus))
      )
    }
  }
}
