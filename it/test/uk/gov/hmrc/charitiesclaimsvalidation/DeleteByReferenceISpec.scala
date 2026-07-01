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

class DeleteByReferenceISpec extends IntegrationTestSupport:

  private val claimId   = "delete-by-ref-test"
  private val reference = "ref-to-delete"
  private val timestamp = Instant.parse("2025-01-01T12:00:00Z")
  private val someFields = Some(Map("foo" -> "bar"))

  override def beforeEach(): Unit =
    super.beforeEach()
    repository.deleteByClaimId(claimId).futureValue

  "DELETE /:claimId/upload-results/:reference" should:

    "return 200 with success=true when deleting an existing upload by reference" in:
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

      repository.findByReference(claimId, reference).futureValue shouldBe defined

      val response = httpClient
        .delete(url"$baseUrl/$claimId/upload-results/$reference")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe OK
      (response.json \ "success").as[Boolean] shouldBe true

      repository.findByReference(claimId, reference).futureValue shouldBe None

    "return 404 when reference does not exist" in:
      val nonExistentRef = "non-existent-ref"

      val response = httpClient
        .delete(url"$baseUrl/$claimId/upload-results/$nonExistentRef")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NOT_FOUND
      (response.json \ "error").as[String] shouldBe "CLAIM_REFERENCE_DOES_NOT_EXIST"

    "delete only the specified reference and leave others intact" in:
      val ref1 = "ref-001"
      val ref2 = "ref-002"

      val status1 = AwaitingUploadStatus(claimId, ref1, ValidationType.GiftAid, "https://upload.com/1", timestamp, Some(Map("foo" -> "bar")), timestamp, timestamp)
      val status2 = VerifyingStatus(claimId, ref2, ValidationType.OtherIncome, timestamp, timestamp)

      repository.insert(status1).futureValue
      repository.insert(status2).futureValue

      repository.findByClaimId(claimId).futureValue should have size 2

      val response = httpClient
        .delete(url"$baseUrl/$claimId/upload-results/$ref1")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe OK

      repository.findByReference(claimId, ref1).futureValue shouldBe None
      repository.findByReference(claimId, ref2).futureValue shouldBe defined

    "return 404 when claimId exists but reference does not match" in:
      val status = AwaitingUploadStatus(claimId, "existing-ref", ValidationType.GiftAid, "https://upload.com/1", timestamp, Some(Map("foo" -> "bar")), timestamp, timestamp)
      repository.insert(status).futureValue

      val response = httpClient
        .delete(url"$baseUrl/$claimId/upload-results/wrong-ref")(HeaderCarrier())
        .execute[HttpResponse]
        .futureValue

      response.status shouldBe NOT_FOUND

      repository.findByReference(claimId, "existing-ref").futureValue shouldBe defined
