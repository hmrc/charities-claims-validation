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
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.{CommunityBuildingRow, ConnectedCharitiesRow, GiftAidDonationRow, OtherIncomeRow}

class DocumentRowSpec extends AnyWordSpec with Matchers:

  "OtherIncomeRow" should:
    "serialize and deserialize OtherIncomeRow correctly" in:
      val request = OtherIncomeRow("1", "Test User", "01/01/25", "1,234.00", "56.00")

      val json   = Json.toJson(request)(OtherIncomeRow.format)
      val parsed = json.as[OtherIncomeRow]

      parsed.payerName shouldBe request.payerName
      parsed.paymentDate shouldBe request.paymentDate
      parsed.grossPayment shouldBe request.grossPayment
      parsed.otherIncomeItem shouldBe request.otherIncomeItem
      parsed.taxDeducted shouldBe request.taxDeducted

  "ConnectedCharitiesRow" should:
    "serialize and deserialize ConnectedCharitiesRow correctly" in:
      val request = ConnectedCharitiesRow("1", "Test Name", "reference1")

      val json   = Json.toJson(request)(ConnectedCharitiesRow.format)
      val parsed = json.as[ConnectedCharitiesRow]

      parsed.charityName shouldBe request.charityName
      parsed.charityReference shouldBe request.charityReference
      parsed.connectedCharitiesItem shouldBe request.connectedCharitiesItem

  "GiftAidDonationRow" should:
    "serialize and deserialize GiftAidDonationRow correctly" in:
      val request = GiftAidDonationRow(
        donationItem = "1",
        donorTitle = "mr",
        donorFirstName = "first",
        donorLastName = "last",
        donorHouse = "test",
        donorPostcode = "lw12lss",
        aggregatedDonations = "123",
        sponsoredEvent = "Yes",
        donationDate = "01/01/25",
        donationAmount = "1.1"
      )

      val json   = Json.toJson(request)(GiftAidDonationRow.format)
      val parsed = json.as[GiftAidDonationRow]

      parsed.donationItem shouldBe request.donationItem
      parsed.donorHouse shouldBe request.donorHouse
      parsed.donorTitle shouldBe request.donorTitle
      parsed.donationDate shouldBe request.donationDate
      parsed.aggregatedDonations shouldBe request.aggregatedDonations
      parsed.donationAmount shouldBe request.donationAmount
      parsed.donorFirstName shouldBe request.donorFirstName
      parsed.donorLastName shouldBe request.donorLastName
      parsed.donorPostcode shouldBe request.donorPostcode

  "CommunityBuildingRow" should:
    "serialize and deserialize CommunityBuildingRow correctly" in:
      val request = CommunityBuildingRow(
        item = "01",
        buildingName = "Test Building",
        firstLineOfAddress = "123 Street",
        postcode = "SW1A1AA",
        taxYear1 = "2023",
        amount1 = "100.00",
        taxYear2 = "2030",
        amount2 = "200.00"
      )

      val json   = Json.toJson(request)(CommunityBuildingRow.format)
      val parsed = json.as[CommunityBuildingRow]

      parsed.item shouldBe request.item
      parsed.buildingName shouldBe request.buildingName
      parsed.firstLineOfAddress shouldBe request.firstLineOfAddress
      parsed.postcode shouldBe request.postcode
      parsed.taxYear1 shouldBe request.taxYear1
      parsed.amount1 shouldBe request.amount1
      parsed.taxYear2 shouldBe request.taxYear2
      parsed.amount2 shouldBe request.amount2
