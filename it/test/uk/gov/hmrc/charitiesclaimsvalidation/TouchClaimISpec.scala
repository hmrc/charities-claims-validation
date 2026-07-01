/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation

import play.api.http.Status.*
import uk.gov.hmrc.charitiesclaimsvalidation.helpers.IntegrationTestSupport
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{AwaitingUploadStatus, ValidationType, VerifyingStatus}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class TouchClaimISpec extends IntegrationTestSupport:

  private val claimId      = "touch-claim-test"
  private val otherClaimId = "touch-other-claim"
  private val timestamp    = Instant.parse("2025-01-01T12:00:00Z")
  private val someFields   = Some(Map("foo" -> "bar"))

  override def beforeEach(): Unit =
    super.beforeEach()
    repository.deleteByClaimId(claimId).futureValue
    repository.deleteByClaimId(otherClaimId).futureValue

  "PATCH /ttl/:claimId" should:

    "return 204 NO_CONTENT when claim exists and updatedAt is touched" in:

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

      repository.insert(status1).futureValue shouldBe true

      val beforeTouch = repository.findByClaimId(claimId).futureValue.head.updatedAt

      val response = httpClient
        .patch(url"$baseUrl/ttl/$claimId")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NO_CONTENT

      val afterTouch = repository.findByClaimId(claimId).futureValue.head.updatedAt

      afterTouch.toEpochMilli should be >= beforeTouch.toEpochMilli

    "return 204 NO_CONTENT when claim does not exist" in:
      repository.findByClaimId(claimId).futureValue shouldBe empty

      val response = httpClient
        .patch(url"$baseUrl/ttl/$claimId")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NO_CONTENT

    "touch only documents for the specified claimId" in:

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
        claimId = otherClaimId,
        reference = "ref-002",
        validationType = ValidationType.OtherIncome,
        createdAt = timestamp,
        updatedAt = timestamp
      )

      repository.insert(status1).futureValue
      repository.insert(status2).futureValue

      val response = httpClient
        .patch(url"$baseUrl/ttl/$claimId")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NO_CONTENT

      val updated   = repository.findByClaimId(claimId).futureValue.head.updatedAt
      val untouched = repository.findByClaimId(otherClaimId).futureValue.head.updatedAt

      updated.isAfter(timestamp) shouldBe true
      untouched shouldBe timestamp
