/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation

import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaimsvalidation.helpers.IntegrationTestSupport
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.*

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class UpscanCallbackISpec extends IntegrationTestSupport:

  private val claimId   = "upscan-callback-test"
  private val reference = "callback-ref"
  private val timestamp = Instant.parse("2025-01-01T12:00:00Z")
  private val someFields = Some(Map("foo" -> "bar"))

  override def beforeEach(): Unit =
    super.beforeEach()
    repository.deleteByClaimId(claimId).futureValue

  "POST /:claimId/upscan-callback" should:

    "return 204 and update status to VALIDATING when receiving success callback from AWAITING_UPLOAD" in:
      val status = AwaitingUploadStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.OtherIncome,
        uploadUrl = "https://upload.com/bucket",
        initiateTimestamp = timestamp,
        fields = someFields,
        createdAt = timestamp,
        updatedAt = timestamp
      )
      repository.insert(status).futureValue shouldBe true

      val successCallback = Json.obj(
        "reference"     -> reference,
        "fileStatus"    -> "READY",
        "downloadUrl"   -> "https://download.com/file",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "other_income_schedule.ods",
          "fileMimeType"    -> "application/vnd.oasis.opendocument.spreadsheet",
          "uploadTimestamp" -> "2025-01-01T12:00:00Z",
          "checksum"        -> "abc123checksum",
          "size"            -> 1024
        )
      )

      val response = httpClient
        .post(url"$baseUrl/$claimId/upscan-callback")(HeaderCarrier())
        .withBody(successCallback)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NO_CONTENT

      val updatedStatus = repository.findByReference(claimId, reference).futureValue.value
      updatedStatus shouldBe a[ValidatingStatus]

    "return 204 and update status to VALIDATING when receiving success callback from VERIFYING" in:
      val status = VerifyingStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.OtherIncome,
        createdAt = timestamp,
        updatedAt = timestamp
      )
      repository.insert(status).futureValue shouldBe true

      val successCallback = Json.obj(
        "reference"     -> reference,
        "fileStatus"    -> "READY",
        "downloadUrl"   -> "https://download.com/file",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "other_income_schedule_Libre.ods",
          "fileMimeType"    -> "application/vnd.oasis.opendocument.spreadsheet",
          "uploadTimestamp" -> "2025-01-01T12:00:00Z",
          "checksum"        -> "abc123checksum",
          "size"            -> 2048
        )
      )

      val response = httpClient
        .post(url"$baseUrl/$claimId/upscan-callback")(HeaderCarrier())
        .withBody(successCallback)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NO_CONTENT

      val updatedStatus = repository.findByReference(claimId, reference).futureValue.value
      updatedStatus shouldBe a[ValidatingStatus]

    "return 204 and update status to VERIFICATION_FAILED when receiving failure callback" in:
      val status = AwaitingUploadStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.OtherIncome,
        uploadUrl = "https://upload.com/bucket",
        initiateTimestamp = timestamp,
        fields = someFields,
        createdAt = timestamp,
        updatedAt = timestamp
      )
      repository.insert(status).futureValue shouldBe true

      val failureCallback = Json.obj(
        "reference"      -> reference,
        "fileStatus"     -> "FAILED",
        "failureDetails" -> Json.obj(
          "failureReason" -> "QUARANTINE",
          "message"       -> "This file contains a virus"
        )
      )

      val response = httpClient
        .post(url"$baseUrl/$claimId/upscan-callback")(HeaderCarrier())
        .withBody(failureCallback)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NO_CONTENT

      val updatedStatus = repository.findByReference(claimId, reference).futureValue.value
      updatedStatus shouldBe a[VerificationFailedStatus]
      updatedStatus.asInstanceOf[VerificationFailedStatus].failureDetails.failureReason shouldBe FailureReason.Quarantine
      updatedStatus.asInstanceOf[VerificationFailedStatus].failureDetails.message shouldBe "This file contains a virus"

    "return 404 when reference does not exist" in:
      val failureCallback = Json.obj(
        "reference"      -> "non-existent-ref",
        "fileStatus"     -> "FAILED",
        "failureDetails" -> Json.obj(
          "failureReason" -> "REJECTED",
          "message"       -> "File rejected"
        )
      )

      val response = httpClient
        .post(url"$baseUrl/$claimId/upscan-callback")(HeaderCarrier())
        .withBody(failureCallback)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NOT_FOUND

    "return 400 when request body has invalid fileStatus" in:
      val invalidCallback = Json.obj(
        "reference"  -> reference,
        "fileStatus" -> "INVALID_STATUS"
      )

      val response = httpClient
        .post(url"$baseUrl/$claimId/upscan-callback")(HeaderCarrier())
        .withBody(invalidCallback)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe BAD_REQUEST

    "return 400 when request body is missing required fields" in:
      val incompleteCallback = Json.obj(
        "reference" -> reference
      )

      val response = httpClient
        .post(url"$baseUrl/$claimId/upscan-callback")(HeaderCarrier())
        .withBody(incompleteCallback)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe BAD_REQUEST

    "return 400 when reference is empty string" in:
      val emptyRefCallback = Json.obj(
        "reference"      -> "",
        "fileStatus"     -> "FAILED",
        "failureDetails" -> Json.obj(
          "failureReason" -> "REJECTED",
          "message"       -> "File rejected"
        )
      )

      val response = httpClient
        .post(url"$baseUrl/$claimId/upscan-callback")(HeaderCarrier())
        .withBody(emptyRefCallback)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe BAD_REQUEST

    "return 204 with no update when success callback received for already processed status" in:
      val status = ValidatedStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.OtherIncome,
        createdAt = timestamp,
        updatedAt = timestamp
      )
      repository.insert(status).futureValue shouldBe true

      val successCallback = Json.obj(
        "reference"     -> reference,
        "fileStatus"    -> "READY",
        "downloadUrl"   -> "https://download.com/file",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "other_income_schedule.ods",
          "fileMimeType"    -> "application/vnd.oasis.opendocument.spreadsheet",
          "uploadTimestamp" -> "2025-01-01T12:00:00Z",
          "checksum"        -> "abc123checksum",
          "size"            -> 1024
        )
      )

      val response = httpClient
        .post(url"$baseUrl/$claimId/upscan-callback")(HeaderCarrier())
        .withBody(successCallback)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NO_CONTENT

      val unchangedStatus = repository.findByReference(claimId, reference).futureValue.value
      unchangedStatus shouldBe a[ValidatedStatus]

    "handle REJECTED failure reason correctly" in:
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

      val failureCallback = Json.obj(
        "reference"      -> reference,
        "fileStatus"     -> "FAILED",
        "failureDetails" -> Json.obj(
          "failureReason" -> "REJECTED",
          "message"       -> "File type not allowed"
        )
      )

      val response = httpClient
        .post(url"$baseUrl/$claimId/upscan-callback")(HeaderCarrier())
        .withBody(failureCallback)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NO_CONTENT

      val updatedStatus = repository.findByReference(claimId, reference).futureValue.value
      updatedStatus shouldBe a[VerificationFailedStatus]
      updatedStatus.asInstanceOf[VerificationFailedStatus].failureDetails.failureReason shouldBe FailureReason.Rejected

    "handle UNKNOWN failure reason correctly" in:
      val status = VerifyingStatus(
        claimId = claimId,
        reference = reference,
        validationType = ValidationType.CommunityBuildings,
        createdAt = timestamp,
        updatedAt = timestamp
      )
      repository.insert(status).futureValue shouldBe true

      val failureCallback = Json.obj(
        "reference"      -> reference,
        "fileStatus"     -> "FAILED",
        "failureDetails" -> Json.obj(
          "failureReason" -> "UNKNOWN",
          "message"       -> "Unknown error occurred"
        )
      )

      val response = httpClient
        .post(url"$baseUrl/$claimId/upscan-callback")(HeaderCarrier())
        .withBody(failureCallback)
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NO_CONTENT

      val updatedStatus = repository.findByReference(claimId, reference).futureValue.value
      updatedStatus shouldBe a[VerificationFailedStatus]
      updatedStatus.asInstanceOf[VerificationFailedStatus].failureDetails.failureReason shouldBe FailureReason.Unknown

    "handle multiple callbacks for same claim with different references" in:
      val ref1 = "ref-001"
      val ref2 = "ref-002"

      repository.insert(VerifyingStatus(claimId, ref1, ValidationType.ConnectedCharities, timestamp, timestamp)).futureValue
      repository.insert(VerifyingStatus(claimId, ref2, ValidationType.OtherIncome, timestamp, timestamp)).futureValue

      val successCallback = Json.obj(
        "reference"     -> ref1,
        "fileStatus"    -> "READY",
        "downloadUrl"   -> "https://s3.amazonaws.com/connected_charities_schedule__Excel_GoodData.ods",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "connected_charities_schedule__Excel_GoodData.ods",
          "fileMimeType"    -> "application/vnd.oasis.opendocument.spreadsheet",
          "uploadTimestamp" -> "2025-01-01T13:00:00Z",
          "checksum"        -> "abc1",
          "size"            -> 100
        )
      )

      httpClient
        .post(url"$baseUrl/$claimId/upscan-callback")(HeaderCarrier())
        .withBody(successCallback)
        .execute[HttpResponse]
        .futureValue
        .status shouldBe NO_CONTENT

      val failureCallback = Json.obj(
        "reference"      -> ref2,
        "fileStatus"     -> "FAILED",
        "failureDetails" -> Json.obj(
          "failureReason" -> "QUARANTINE",
          "message"       -> "Virus found"
        )
      )

      httpClient
        .post(url"$baseUrl/$claimId/upscan-callback")(HeaderCarrier())
        .withBody(failureCallback)
        .execute[HttpResponse]
        .futureValue
        .status shouldBe NO_CONTENT

      val status1 = repository.findByReference(claimId, ref1).futureValue.value
      status1 shouldBe a[ValidatingStatus]

      val status2 = repository.findByReference(claimId, ref2).futureValue.value
      status2 shouldBe a[VerificationFailedStatus]

      val summary = repository.getClaimSummary(claimId).futureValue.value
      summary.uploads should have size 2
