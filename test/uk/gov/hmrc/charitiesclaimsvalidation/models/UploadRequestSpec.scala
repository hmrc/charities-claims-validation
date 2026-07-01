/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models

import eu.timepit.refined.types.string.NonEmptyString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.ValidationType
import uk.gov.hmrc.charitiesclaimsvalidation.models.requests.UploadRequest
import eu.timepit.refined.types.all.NonEmptyString

import java.net.URI
import java.time.Instant

class UploadRequestSpec extends AnyWordSpec with Matchers:

  private val ref1      = NonEmptyString.unsafeFrom("ref-uuid-001")
  private val timestamp = Instant.parse("2025-01-01T12:00:00Z")

  "UploadRequest" should:
    "serialize and deserialize UploadRequest correctly" in:
      val request = UploadRequest(
        reference = ref1,
        validationType = ValidationType.GiftAid,
        uploadUrl = URI.create("https://test-url-1"),
        initiateTimestamp = timestamp,
        fields = Some(Map("foo" -> "bar"))
      )

      val json   = Json.toJson(request)(UploadRequest.format)
      val parsed = json.as[UploadRequest]

      parsed.reference shouldBe request.reference
      parsed.validationType shouldBe request.validationType
      parsed.initiateTimestamp shouldBe timestamp
