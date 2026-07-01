/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*

import java.time.LocalDate

class BigDecimalFormattingSpec extends AnyWordSpec with Matchers:

  "BigDecimal formatting" should:

    "format whole numbers with two decimal places in GiftAidDonation" in:
      val donation = GiftAidDonation(
        donationItem = 1,
        donorTitle = Some("Mr"),
        donorFirstName = Some("John"),
        donorLastName = Some("Doe"),
        donorHouse = Some("123"),
        donorPostcode = Some("AB1 2CD"),
        aggregatedDonations = None,
        sponsoredEvent = false,
        donationDate = LocalDate.of(2025, 1, 15),
        donationAmount = BigDecimal(1450)
      )

      val json = Json.toJson(donation)
      (json \ "donationAmount").as[String] shouldBe "1450.00"

    "format decimal numbers with two decimal places in GiftAidDonation" in:
      val donation = GiftAidDonation(
        donationItem = 1,
        donorTitle = Some("Mr"),
        donorFirstName = Some("John"),
        donorLastName = Some("Doe"),
        donorHouse = Some("123"),
        donorPostcode = Some("AB1 2CD"),
        aggregatedDonations = None,
        sponsoredEvent = false,
        donationDate = LocalDate.of(2025, 1, 15),
        donationAmount = BigDecimal("240.50")
      )

      val json = Json.toJson(donation)
      (json \ "donationAmount").as[String] shouldBe "240.50"

    "format totalDonations with two decimal places in GiftAidScheduleData" in:
      val data = GiftAidScheduleData(
        earliestDonationDate = Some(LocalDate.of(2025, 1, 1)),
        prevOverclaimedGiftAid = Some(BigDecimal("100.00")),
        totalDonations = Some(BigDecimal(1450)),
        donations = Nil
      )

      val json = Json.toJson(data)
      (json \ "totalDonations").as[String] shouldBe "1450.00"

    "default prevOverclaimedGiftAid to 0.00 when None in GiftAidScheduleData" in:
      val data = GiftAidScheduleData(
        earliestDonationDate = Some(LocalDate.of(2025, 1, 1)),
        prevOverclaimedGiftAid = None,
        totalDonations = Some(BigDecimal("500.00")),
        donations = Nil
      )

      val json = Json.toJson(data)
      (json \ "prevOverclaimedGiftAid").as[String] shouldBe "0.00"

    "format prevOverclaimedGiftAid with two decimal places when provided" in:
      val data = GiftAidScheduleData(
        earliestDonationDate = Some(LocalDate.of(2025, 1, 1)),
        prevOverclaimedGiftAid = Some(BigDecimal(250)),
        totalDonations = Some(BigDecimal("500.00")),
        donations = Nil
      )

      val json = Json.toJson(data)
      (json \ "prevOverclaimedGiftAid").as[String] shouldBe "250.00"

    "format OtherIncome monetary fields with two decimal places" in:
      val income = OtherIncome(
        otherIncomeItem = 1,
        payerName = "Test Payer",
        paymentDate = LocalDate.of(2025, 1, 15),
        grossPayment = BigDecimal(1000),
        taxDeducted = BigDecimal(200)
      )

      val json = Json.toJson(income)
      (json \ "grossPayment").as[String] shouldBe "1000.00"
      (json \ "taxDeducted").as[String] shouldBe "200.00"

    "default adjustmentForOtherIncomePreviousOverClaimed to 0.00 when None" in:
      val data = OtherIncomeData(
        adjustmentForOtherIncomePreviousOverClaimed = None,
        totalOfGrossPayments = None,
        totalOfTaxDeducted = None,
        otherIncomes = Nil
      )

      val json = Json.toJson(data)
      (json \ "adjustmentForOtherIncomePreviousOverClaimed").as[String] shouldBe "0.00"

    "format CommunityBuilding monetary fields with two decimal places" in:
      val building = CommunityBuilding(
        communityBuildingItem = 1,
        buildingName = "St Mary's Church",
        firstLineOfAddress = "123 Church Street",
        postcode = "SW1A 1AA",
        taxYear1 = 2023,
        amountYear1 = BigDecimal(1500),
        taxYear2 = Some(2024),
        amountYear2 = Some(BigDecimal(2000))
      )

      val json = Json.toJson(building)
      (json \ "amountYear1").as[String] shouldBe "1500.00"
      (json \ "amountYear2").as[String] shouldBe "2000.00"
