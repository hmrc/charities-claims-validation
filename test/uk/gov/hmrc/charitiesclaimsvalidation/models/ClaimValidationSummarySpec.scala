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

package uk.gov.hmrc.charitiesclaimsvalidation.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{AwaitingUploadStatus, ClaimValidationStatus, ClaimValidationSummary, ValidationType, VerifyingStatus}

import java.time.Instant

class ClaimValidationSummarySpec extends AnyWordSpec with Matchers:

  private val claimId   = "test-claim-123"
  private val ref1      = "ref-uuid-001"
  private val ref2      = "ref-uuid-002"
  private val timestamp = Instant.parse("2025-01-01T12:00:00Z")

  private val validationStatus1 = AwaitingUploadStatus(
    claimId = claimId,
    reference = ref1,
    validationType = ValidationType.GiftAid,
    uploadUrl = "https://test-url-1",
    initiateTimestamp = timestamp,
    fields = Some(Map("foo" -> "bar")),
    createdAt = timestamp,
    updatedAt = timestamp
  )

  private val validationStatus2 = VerifyingStatus(
    claimId = claimId,
    reference = ref2,
    validationType = ValidationType.OtherIncome,
    createdAt = timestamp,
    updatedAt = timestamp
  )

  "ClaimValidationSummary" should:
    "serialize and deserialize correctly" in:
      val summary = ClaimValidationSummary(
        claimId = claimId,
        uploads = Seq(validationStatus1, validationStatus2)
      )

      val json   = Json.toJson(summary)
      val parsed = json.as[ClaimValidationSummary]

      parsed.claimId shouldBe claimId
      parsed.uploads should have size 2
      parsed.uploads.map(_.reference) should contain allOf (ref1, ref2)

    "map claimId and reference to compound _id in JSON" in:
      import play.api.libs.json.*

      val json = Json.toJson(validationStatus1)(ClaimValidationStatus.format)

      (json \ "_id" \ "claimId").as[String] shouldBe claimId
      (json \ "_id" \ "reference").as[String] shouldBe ref1
