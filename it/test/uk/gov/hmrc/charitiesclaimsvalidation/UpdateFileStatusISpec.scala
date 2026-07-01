/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation

import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaimsvalidation.helpers.IntegrationTestSupport
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{AwaitingUploadStatus, ValidationType, VerifyingStatus}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.*

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class UpdateFileStatusISpec extends IntegrationTestSupport:

  private val claimId   = "update-status-test"
  private val reference = "ref-to-update"
  private val timestamp = Instant.parse("2025-01-01T12:00:00Z")
  private val someFields = Some(Map("foo" -> "bar"))

  override def beforeEach(): Unit =
    super.beforeEach()
    repository.deleteByClaimId(claimId).futureValue

  "PUT /:claimId/upload-results/:reference" should:

    "return 200 with success=true when updating from AWAITING_UPLOAD to VERIFYING" in:
      val status = AwaitingUploadStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        uploadUrl = "https://upload.com/bucket",
        initiateTimestamp = timestamp,
        fields = someFields,
        createdAt = timestamp,
        updatedAt = timestamp
      )
      repository.insert(status).futureValue shouldBe true

      val requestBody = Json.obj("fileStatus" -> "VERIFYING")

      val response = httpClient
        .put(url"$baseUrl/$claimId/upload-results/$reference")(HeaderCarrier())
        .withBody(requestBody)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe OK
      (response.json \ "success").as[Boolean] shouldBe true

      val updatedStatus = repository.findByReference(claimId, reference).futureValue.value
      updatedStatus shouldBe a[VerifyingStatus]

    "return 200 with success=true when status already transitioned (idempotent)" in:
      val status = VerifyingStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.OtherIncome,
        createdAt = timestamp,
        updatedAt = timestamp
      )
      repository.insert(status).futureValue shouldBe true

      val requestBody = Json.obj("fileStatus" -> "VERIFYING")

      val response = httpClient
        .put(url"$baseUrl/$claimId/upload-results/$reference")(HeaderCarrier())
        .withBody(requestBody)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe OK
      (response.json \ "success").as[Boolean] shouldBe true

    "return 404 when reference does not exist" in:
      val requestBody = Json.obj("fileStatus" -> "VERIFYING")

      val response = httpClient
        .put(url"$baseUrl/$claimId/upload-results/non-existent-ref")(HeaderCarrier())
        .withBody(requestBody)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NOT_FOUND
      (response.json \ "error").as[String] shouldBe "CLAIM_REFERENCE_DOES_NOT_EXIST"

    "return 400 when requesting invalid status transition (not VERIFYING)" in:
      val status = AwaitingUploadStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.GiftAid,
        uploadUrl = "https://upload.com/bucket",
        initiateTimestamp = timestamp,
        fields = someFields,
        createdAt = timestamp,
        updatedAt = timestamp
      )
      repository.insert(status).futureValue

      val requestBody = Json.obj("fileStatus" -> "VALIDATED")

      val response = httpClient
        .put(url"$baseUrl/$claimId/upload-results/$reference")(HeaderCarrier())
        .withBody(requestBody)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe BAD_REQUEST

    "return 400 when request body is invalid JSON" in:
      val status = AwaitingUploadStatus(claimId, reference, ValidationType.GiftAid, "https://upload.com/1", timestamp, someFields, timestamp, timestamp)
      repository.insert(status).futureValue

      val requestBody = Json.obj("invalid" -> "field")

      val response = httpClient
        .put(url"$baseUrl/$claimId/upload-results/$reference")(HeaderCarrier())
        .withBody(requestBody)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe BAD_REQUEST

    "return 400 when fileStatus value is not a valid status" in:
      val status = AwaitingUploadStatus(claimId, reference, ValidationType.GiftAid, "https://upload.com/1", timestamp, someFields, timestamp, timestamp)
      repository.insert(status).futureValue

      val requestBody = Json.obj("fileStatus" -> "INVALID_STATUS")

      val response = httpClient
        .put(url"$baseUrl/$claimId/upload-results/$reference")(HeaderCarrier())
        .withBody(requestBody)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe BAD_REQUEST

    "update only the specified reference" in:
      val ref1 = "ref-001"
      val ref2 = "ref-002"

      val status1 = AwaitingUploadStatus(claimId, ref1, ValidationType.GiftAid, "https://upload.com/1", timestamp, someFields, timestamp, timestamp)
      val status2 = AwaitingUploadStatus(claimId, ref2, ValidationType.OtherIncome, "https://upload.com/2", timestamp,someFields,  timestamp, timestamp)

      repository.insert(status1).futureValue
      repository.insert(status2).futureValue

      val requestBody = Json.obj("fileStatus" -> "VERIFYING")

      val response = httpClient
        .put(url"$baseUrl/$claimId/upload-results/$ref1")(HeaderCarrier())
        .withBody(requestBody)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe OK

      val updated1 = repository.findByReference(claimId, ref1).futureValue.value
      updated1 shouldBe a[VerifyingStatus]

      val unchanged2 = repository.findByReference(claimId, ref2).futureValue.value
      unchanged2 shouldBe a[AwaitingUploadStatus]
