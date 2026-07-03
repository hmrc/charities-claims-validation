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
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{AwaitingUploadStatus, Charity, ClaimValidationStatus, CommunityBuilding, CommunityBuildingData, ConnectedCharitiesData, FailureDetails, FailureReason, GiftAidDonation, GiftAidScheduleData, ValidatedStatus, ValidationError, ValidationFailedStatus, ValidationType, VerificationFailedStatus}

import java.time.{Instant, LocalDate}

class ClaimValidationStatusSpec extends AnyWordSpec with Matchers:

  private val claimId   = "test-claim-123"
  private val ref1      = "ref-uuid-001"
  private val timestamp = Instant.parse("2025-01-01T12:00:00Z")

  "ClaimValidationStatus" should:
    "serialize and deserialize AwaitingUploadStatus correctly" in:
      val status = AwaitingUploadStatus(
        claimId = claimId,
        reference = ref1,
        validationType = ValidationType.GiftAid,
        uploadUrl = "https://test-url-1",
        fields = Some(Map("foo" -> "bar")),
        initiateTimestamp = timestamp,
        createdAt = timestamp,
        updatedAt = timestamp
      )

      val json   = Json.toJson(status)(ClaimValidationStatus.format)
      val parsed = json.as[ClaimValidationStatus]

      parsed.claimId shouldBe status.claimId
      parsed.reference shouldBe status.reference
      parsed.validationType shouldBe status.validationType
      parsed.fileStatus shouldBe "AWAITING_UPLOAD"

    "serialize and deserialize VerificationFailedStatus with failure details" in:
      val statusWithFailure = VerificationFailedStatus(
        claimId = claimId,
        reference = ref1,
        validationType = ValidationType.GiftAid,
        failureDetails = FailureDetails(FailureReason.Quarantine, "File contains virus"),
        createdAt = timestamp,
        updatedAt = timestamp
      )

      val json   = Json.toJson(statusWithFailure)(ClaimValidationStatus.format)
      val parsed = json.as[ClaimValidationStatus]

      parsed shouldBe a[VerificationFailedStatus]
      val verificationFailed = parsed.asInstanceOf[VerificationFailedStatus]
      verificationFailed.failureDetails.failureReason shouldBe FailureReason.Quarantine

    "serialize and deserialize ValidationFailedStatus with validation errors" in:
      val statusWithErrors = ValidationFailedStatus(
        claimId = claimId,
        reference = ref1,
        validationType = ValidationType.GiftAid,
        errors = Seq(
          ValidationError("donations[0]", "Invalid donation amount")
        ),
        createdAt = timestamp,
        updatedAt = timestamp
      )

      val json   = Json.toJson(statusWithErrors)(ClaimValidationStatus.format)
      val parsed = json.as[ClaimValidationStatus]

      parsed shouldBe a[ValidationFailedStatus]
      val validationFailed = parsed.asInstanceOf[ValidationFailedStatus]
      validationFailed.errors should have size 1
      validationFailed.errors.head.field shouldBe "donations[0]"

    "serialize and deserialize ValidatedStatus with GiftAid data" in:
      val statusWithData = ValidatedStatus(
        claimId = claimId,
        reference = ref1,
        validationType = ValidationType.GiftAid,
        giftAidScheduleData = Some(
          GiftAidScheduleData(
            earliestDonationDate = Some(LocalDate.of(2025, 1, 1)),
            prevOverclaimedGiftAid = Some(BigDecimal(0)),
            totalDonations = Some(100),
            donations = List(
              GiftAidDonation(
                donationItem = 1,
                donorTitle = Some("Mr"),
                donorFirstName = Some("John"),
                donorLastName = Some("Doe"),
                donorHouse = Some("123"),
                donorPostcode = Some("AB1 2CD"),
                aggregatedDonations = None,
                sponsoredEvent = false,
                donationDate = LocalDate.of(2025, 1, 15),
                donationAmount = BigDecimal(100)
              )
            )
          )
        ),
        createdAt = timestamp,
        updatedAt = timestamp
      )

      val json   = Json.toJson(statusWithData)(ClaimValidationStatus.format)
      val parsed = json.as[ClaimValidationStatus]

      parsed shouldBe a[ValidatedStatus]
      val validated = parsed.asInstanceOf[ValidatedStatus]
      validated.giftAidScheduleData shouldBe defined
      validated.giftAidScheduleData.get.earliestDonationDate shouldBe Some(LocalDate.of(2025, 1, 1))
      validated.giftAidScheduleData.get.donations should have size 1

    "serialize and deserialize ValidatedStatus with CommunityBuilding data" in:
      val statusWithData = ValidatedStatus(
        claimId = claimId,
        reference = ref1,
        validationType = ValidationType.CommunityBuildings,
        communityBuildingsData = Some(
          CommunityBuildingData(
            Some(BigDecimal("3500.00")),
            communityBuildings = List(
              CommunityBuilding(
                communityBuildingItem = 1,
                buildingName = "St Mary's Church",
                firstLineOfAddress = "123 Church Street",
                postcode = "SW1A 1AA",
                taxYear1 = 2023,
                amountYear1 = BigDecimal("1500.00"),
                taxYear2 = Some(2024),
                amountYear2 = Some(BigDecimal("2000.00"))
              )
            )
          )
        ),
        createdAt = timestamp,
        updatedAt = timestamp
      )

      val json   = Json.toJson(statusWithData)(ClaimValidationStatus.format)
      val parsed = json.as[ClaimValidationStatus]

      parsed shouldBe a[ValidatedStatus]
      val validated = parsed.asInstanceOf[ValidatedStatus]
      validated.communityBuildingsData shouldBe defined
      validated.communityBuildingsData.get.totalOfAllAmounts shouldBe defined
      validated.communityBuildingsData.get.communityBuildings should have size 1
      validated.communityBuildingsData.get.communityBuildings.head.buildingName shouldBe "St Mary's Church"
      validated.communityBuildingsData.get.communityBuildings.head.taxYear1 shouldBe 2023

    "serialize and deserialize ValidatedStatus with Connected Charities data" in:
      val connectedData = ConnectedCharitiesData(
        List(
          Charity(
            charityItem = 1,
            charityName = "Charity of the 501st Legion",
            charityReference = "CW501"
          )
        )
      )

      val statusWithData = ValidatedStatus(
        claimId = claimId,
        reference = ref1,
        validationType = ValidationType.ConnectedCharities,
        connectedCharitiesData = Some(connectedData),
        createdAt = timestamp,
        updatedAt = timestamp
      )
      val json   = Json.toJson(statusWithData)(ClaimValidationStatus.format)
      val parsed = json.as[ClaimValidationStatus]

      parsed shouldBe a[ValidatedStatus]
      val validated = parsed.asInstanceOf[ValidatedStatus]
      validated.connectedCharitiesData shouldBe defined
      validated.connectedCharitiesData.get.charities shouldBe connectedData.charities
      validated.fileStatus shouldBe "VALIDATED"
