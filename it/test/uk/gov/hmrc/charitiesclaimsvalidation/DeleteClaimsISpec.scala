/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation

import play.api.test.Helpers.*
import uk.gov.hmrc.charitiesclaimsvalidation.helpers.IntegrationTestSupport
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{AwaitingUploadStatus, ValidationType, VerifyingStatus}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.*

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class DeleteClaimsISpec extends IntegrationTestSupport:

  private val claimId   = "delete-claims-test"
  private val timestamp = Instant.parse("2025-01-01T12:00:00Z")
  private val someFields = Some(Map("foo" -> "bar"))

  override def beforeEach(): Unit =
    super.beforeEach()
    repository.deleteByClaimId(claimId).futureValue

  "DELETE /:claimId/upload-results" should:

    "return 200 with success=true when deleting all uploads for a claim" in:
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

      repository.findByClaimId(claimId).futureValue should have size 2

      val response = httpClient
        .delete(url"$baseUrl/$claimId/upload-results")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe OK
      (response.json \ "success").as[Boolean] shouldBe true

      repository.findByClaimId(claimId).futureValue shouldBe empty

    "return 200 with success=true when no uploads exist for the claim" in:
      repository.findByClaimId(claimId).futureValue shouldBe empty

      val response = httpClient
        .delete(url"$baseUrl/$claimId/upload-results")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe OK
      (response.json \ "success").as[Boolean] shouldBe true

    "delete only uploads for the specified claimId" in:
      val otherClaimId = "other-claim-id"

      val status1 = AwaitingUploadStatus(claimId, "ref-001", ValidationType.GiftAid, "https://upload.com/1", timestamp, Some(Map("foo" -> "bar")), timestamp, timestamp)
      val status2 = AwaitingUploadStatus(otherClaimId, "ref-002", ValidationType.OtherIncome, "https://upload.com/2", timestamp, Some(Map("foo" -> "bar")), timestamp, timestamp)

      repository.insert(status1).futureValue
      repository.insert(status2).futureValue

      val response = httpClient
        .delete(url"$baseUrl/$claimId/upload-results")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe OK

      repository.findByClaimId(claimId).futureValue shouldBe empty
      repository.findByClaimId(otherClaimId).futureValue should have size 1

      repository.deleteByClaimId(otherClaimId).futureValue
