/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation

import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaimsvalidation.helpers.IntegrationTestSupport
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{AwaitingUploadStatus, ValidatedStatus, ValidationType, VerifyingStatus}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.*

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class GetUploadSummaryISpec extends IntegrationTestSupport:

  private val claimId   = "get-summary-test"
  private val timestamp = Instant.now()
  private val someFields = Some(Map("foo" -> "bar"))

  override def beforeEach(): Unit =
    super.beforeEach()
    repository.deleteByClaimId(claimId).futureValue

  "GET /:claimId/upload-results" should:

    "return 200 with single upload in summary" in:
      val status = ValidatedStatus(
        claimId = claimId,
        reference = "ref-single",
        validationType = ValidationType.CommunityBuildings,
        createdAt = timestamp,
        updatedAt = timestamp
      )
      repository.insert(status).futureValue shouldBe true

      val response = httpClient
        .get(url"$baseUrl/$claimId/upload-results")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe OK
      val uploads = (response.json \ "uploads").as[Seq[play.api.libs.json.JsValue]]
      uploads should have size 1
      (uploads.head \ "validationType").as[String] shouldBe "CommunityBuildings"
      (uploads.head \ "fileStatus").as[String] shouldBe "VALIDATED"

    "return 200 with upload summary containing multiple uploads" in:
      val status1 = AwaitingUploadStatus(
        claimId = claimId,
        reference = "ref-001",
        validationType = ValidationType.GiftAid,
        uploadUrl = "https://upload.com/bucket1",
        initiateTimestamp = timestamp,
        fields = someFields,
        createdAt = timestamp,
        updatedAt = timestamp
      )
      val status2 = VerifyingStatus(
        claimId = claimId,
        reference = "ref-002",
        validationType = ValidationType.OtherIncome,
        createdAt = timestamp,
        updatedAt = timestamp
      )
      repository.insert(status1).futureValue shouldBe true
      repository.insert(status2).futureValue shouldBe true

      val response = httpClient
        .get(url"$baseUrl/$claimId/upload-results")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe OK
      val uploads = (response.json \ "uploads").as[Seq[play.api.libs.json.JsValue]]
      uploads should have size 2

      val giftAidUpload = uploads.find(u => (u \ "validationType").as[String] == "GiftAid").value
      (giftAidUpload \ "reference").as[String] shouldBe "ref-001"
      (giftAidUpload \ "fileStatus").as[String] shouldBe "AWAITING_UPLOAD"
      (giftAidUpload \ "uploadUrl").asOpt[String] shouldBe Some("https://upload.com/bucket1")

      val otherIncomeUpload = uploads.find(u => (u \ "validationType").as[String] == "OtherIncome").value
      (otherIncomeUpload \ "reference").as[String] shouldBe "ref-002"
      (otherIncomeUpload \ "fileStatus").as[String] shouldBe "VERIFYING"
      (otherIncomeUpload \ "uploadUrl").asOpt[String] shouldBe None

    "return 404 when no uploads exist for the claim" in:
      repository.findByClaimId(claimId).futureValue shouldBe empty

      val response = httpClient
        .get(url"$baseUrl/$claimId/upload-results")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NOT_FOUND
      (response.json \ "error").as[String] shouldBe "CLAIM_DOES_NOT_EXIST"
      (response.json \ "message").as[String] should include(claimId)

    "return 404 and purge expired AWAITING_UPLOAD entries (7+ days old)" in:
      val expiredTimestamp = Instant.now().minus(8, ChronoUnit.DAYS)
      val expiredStatus = AwaitingUploadStatus(
        claimId = claimId,
        reference = "expired-ref",
        validationType = ValidationType.GiftAid,
        uploadUrl = "https://upload.com/expired",
        initiateTimestamp = expiredTimestamp,
        fields = someFields,
        createdAt = expiredTimestamp,
        updatedAt = expiredTimestamp
      )
      repository.insert(expiredStatus).futureValue shouldBe true

      repository.findByClaimId(claimId).futureValue should have size 1

      val response = httpClient
        .get(url"$baseUrl/$claimId/upload-results")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NOT_FOUND
      (response.json \ "error").as[String] shouldBe "CLAIM_DOES_NOT_EXIST"

      // Verify expired upload was deleted from DB
      repository.findByClaimId(claimId).futureValue shouldBe empty

    "return 200 with non-expired uploads after purging expired ones" in:
      val expiredTimestamp = Instant.now().minus(8, ChronoUnit.DAYS)
      val validTimestamp   = Instant.now()

      val expiredStatus = AwaitingUploadStatus(
        claimId = claimId,
        reference = "expired-ref",
        validationType = ValidationType.GiftAid,
        uploadUrl = "https://upload.com/expired",
        initiateTimestamp = expiredTimestamp,
        fields = someFields,
        createdAt = expiredTimestamp,
        updatedAt = expiredTimestamp
      )
      val validStatus = VerifyingStatus(
        claimId = claimId,
        reference = "valid-ref",
        validationType = ValidationType.OtherIncome,
        createdAt = validTimestamp,
        updatedAt = validTimestamp
      )

      repository.insert(expiredStatus).futureValue shouldBe true
      repository.insert(validStatus).futureValue shouldBe true

      repository.findByClaimId(claimId).futureValue should have size 2

      val response = httpClient
        .get(url"$baseUrl/$claimId/upload-results")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe OK
      val uploads = (response.json \ "uploads").as[Seq[play.api.libs.json.JsValue]]
      uploads should have size 1
      (uploads.head \ "reference").as[String] shouldBe "valid-ref"

      repository.findByClaimId(claimId).futureValue should have size 1

    "return summary for only the specified claimId" in:
      val otherClaimId = "other-claim-id"

      val status1 = AwaitingUploadStatus(claimId, "ref-001", ValidationType.GiftAid, "https://upload.com/1", timestamp, Some(Map("foo" -> "bar")), timestamp, timestamp)
      val status2 = AwaitingUploadStatus(otherClaimId, "ref-002", ValidationType.OtherIncome, "https://upload.com/2", timestamp, Some(Map("foo" -> "bar")),  timestamp, timestamp)

      repository.insert(status1).futureValue
      repository.insert(status2).futureValue

      val response = httpClient
        .get(url"$baseUrl/$claimId/upload-results")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe OK
      val uploads = (response.json \ "uploads").as[Seq[play.api.libs.json.JsValue]]
      uploads should have size 1
      (uploads.head \ "reference").as[String] shouldBe "ref-001"

      repository.deleteByClaimId(otherClaimId).futureValue
