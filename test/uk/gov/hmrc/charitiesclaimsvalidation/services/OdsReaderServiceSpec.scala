/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services

import cats.effect.unsafe.implicits.global
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.*
import uk.gov.hmrc.charitiesclaimsvalidation.services.OdsReaderServiceSpec.*
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.OdsReaderService.{cellFromDocument, rowsFromDocument, withDocument, withDocumentStream}
import uk.gov.hmrc.charitiesclaimsvalidation.util.BaseSpec

class OdsReaderServiceSpec extends BaseSpec {

  "OdsReaderService" - {
    "read an ods document and extract the values as strings" in {

      val result = withDocument(OtherIncomeGoodDataPath)(doc => rowsFromDocument[OtherIncomeRow](doc, OtherIncomeRow.layout)).unsafeRunSync().head

      result shouldBe OtherIncomeRow("1", "Test User", "01/01/25", "1,234.00", "56.00")
    }

    "read an ods document and extract a single cell value as string" in {
      val result =
        withDocument(OtherIncomeGoodDataPath)(doc => cellFromDocument(doc, OtherIncomeRow.ADJ_OTHER_INCOME_PREV_CLAIM_FIELD)).unsafeRunSync()

      result shouldBe "78.00"
    }

    "read using remote Url" in {
      val fileUrl =
        new java.io.File(OtherIncomeGoodDataPath).toURI.toURL.toString

      val result = withDocumentStream(fileUrl)(doc => rowsFromDocument[OtherIncomeRow](doc, OtherIncomeRow.layout)).unsafeRunSync().head

      result shouldBe OtherIncomeRow("1", "Test User", "01/01/25", "1,234.00", "56.00")

    }

    "read Community Building data from ods document" in {
      val result = withDocument(CommunityBuildingGoodDataPath)(doc => rowsFromDocument[CommunityBuildingRow](doc, CommunityBuildingRow.layout))
        .unsafeRunSync()
        .head

      result.item should not be empty
      result.buildingName should not be empty
      result.firstLineOfAddress should not be empty
      result.postcode should not be empty
      result.taxYear1 should not be empty
      result.amount1 should not be empty
    }

    "return empty list when rowsFromDocument encounters out-of-bounds row indices" in {
      val outOfBoundsLayout = SheetLayout(rowRange = 99999 to 99999, cellRange = 0 to 5)

      val result = withDocument(OtherIncomeGoodDataPath)(doc => rowsFromDocument[OtherIncomeRow](doc, outOfBoundsLayout))
        .unsafeRunSync()

      result shouldBe empty
    }

    "return empty string for cellFromDocument when row index is out of bounds" in {
      val outOfBoundsCell = SheetCell(rowIndex = 99999, cellIndex = 0)

      val result = withDocument(OtherIncomeGoodDataPath)(doc => cellFromDocument(doc, outOfBoundsCell))
        .unsafeRunSync()

      result shouldBe ""
    }

    "return empty string for cellFromDocument when cell index is out of bounds" in {
      val outOfBoundsCell = SheetCell(rowIndex = 24, cellIndex = 99999)

      val result = withDocument(OtherIncomeGoodDataPath)(doc => cellFromDocument(doc, outOfBoundsCell))
        .unsafeRunSync()

      result shouldBe ""
    }

    "read Gift Aid data from ods document with columns expanded when row has an attribute number columns repeated" in {
      val result = withDocument(GiftAidGoodDataPath)(doc => rowsFromDocument[GiftAidDonationRow](doc, GiftAidDonationRow.layout))
        .unsafeRunSync()

      result.headOption.value.donationItem should not be empty
      result.headOption.value.donorTitle should not be empty
      result.headOption.value.donorFirstName should not be empty
      result.headOption.value.donorLastName should not be empty
      result.headOption.value.donorHouse should not be empty
      result.headOption.value.donorPostcode should not be empty
      result.headOption.value.donationDate should not be empty
      result.headOption.value.donationAmount should not be empty

      result.lift(2).value.donationItem shouldBe "3"
      result.lift(2).value.aggregatedDonations shouldBe "One off Gift Aid donations"
    }

    "read Gift Aid data from ods document with rows expanded when row has an attribute number rows repeated, " +
      "text:s, text:span, text:c attribute correctly parsed" in {
        val result = withDocument(GiftAidGoodDataWithAttributesPath)(doc => rowsFromDocument[GiftAidDonationRow](doc, GiftAidDonationRow.layout))
          .unsafeRunSync()

        result.size shouldBe 7
        result shouldBe expectedResultGiftDataWithAttributes
      }

    "read Other Income data from ods document with rows expanded when row has an attribute number rows repeated, " +
      "text:s, text:span, text:c attribute correctly parsed" in {
        val result = withDocument(OtherIncomeGoodDataWithAttributesPath)(doc => rowsFromDocument[OtherIncomeRow](doc, OtherIncomeRow.layout))
          .unsafeRunSync()

        result shouldBe expectedResultOtherIncomeWithAttributes
      }

    "read Community building data from ods document with rows expanded when row has an attribute number rows repeated, " +
      "text:s, text:span, text:c attribute correctly parsed" in {
        val result =
          withDocument(CommunityBuildingGoodDataWithAttributesPath)(doc => rowsFromDocument[CommunityBuildingRow](doc, CommunityBuildingRow.layout))
            .unsafeRunSync()

        result shouldBe expectedResultCommBuidingWithAttributes
      }

    "read Connected Charities data from ods document with rows expanded when row has an attribute number rows repeated, " +
      "text:s, text:span, text:c attribute correctly parsed" in {
        val result = withDocument(connectedCharitiesGoodDataWithAttributesPath)(doc =>
          rowsFromDocument[ConnectedCharitiesRow](doc, ConnectedCharitiesRow.layout)
        )
          .unsafeRunSync()

        result shouldBe expectedResultConnectedCharitiesWithAttributes
      }
  }
}

object OdsReaderServiceSpec {
  val OtherIncomeGoodDataPath                     = "test/resources/otherincome/other_income_schedule-GoodData.ods"
  val OtherIncomeGoodDataWithAttributesPath       = "test/resources/otherincome/other_income_schedule-GoodDataWithAttributes.ods"
  val CommunityBuildingGoodDataPath               = "test/resources/communitybuildings/community_buildings_excel-GoodData.ods"
  val GiftAidGoodDataPath                         = "test/resources/giftAid/Gift-Aid-Schedule-Excel-GoodData.ods"
  val GiftAidGoodDataWithAttributesPath           = "test/resources/giftAid/Gift-Aid-Schedule-Excel-GoodDataWithAttributes.ods"
  val CommunityBuildingGoodDataWithAttributesPath = "test/resources/communitybuildings/community_buildings_excel-GoodDataWithAttributes.ods"
  val connectedCharitiesGoodDataWithAttributesPath =
    "test/resources/connectedCharities/connected_charities_schedule__Excel_GoodDataWithAttributes.ods"
  val expectedResultGiftDataWithAttributes: Seq[GiftAidDonationRow] = List(
    GiftAidDonationRow("1", "Prof", "Henry", "House Martin", "152A", "M99 2QD", "", "", "24/03/15", "240.00"),
    GiftAidDonationRow("2", "Mr", "John", "Smith", "100      Champs Elysees,\n Paris", "X", "", "", "24/06/15", "250.00"),
    GiftAidDonationRow("3", "", "", "", "", "", "One off Gift Aid donations", "", "31/03/15", "880.00"),
    GiftAidDonationRow("4", "Miss", "B", "Chaudry", "21", "L43 4FB", "", "Yes", "26/04/15", "80.00"),
    GiftAidDonationRow("4", "Miss", "B", "Chaudry", "21", "L43 4FB", "", "Yes", "26/04/15", "80.00"),
    GiftAidDonationRow("4", "Miss", "B", "Chaudry", "21", "L43 4FB", "", "Yes", "28/02/25", "80.00"),
    GiftAidDonationRow("1000", "Mr", "happy", "House del", "152A", "M99 2QD", "", "", "28/03/15", "240.00")
  )

  val expectedResultCommBuidingWithAttributes: Seq[CommunityBuildingRow] = List(
    CommunityBuildingRow("1", "The Vault - Test name", "50 \"Helloworld\" lane", "L20 3UD", "2023", "1,500.00", "2024", "2,500.00"),
    CommunityBuildingRow("2", "Test        Building        Name", "39\n    Kingsbury.\nStreet", "L20 3UD", "2025", "2,000.00", "", ""),
    CommunityBuildingRow("3", "Bootle Village\n\nHall", "Address:            '11A Grange Road'", "L20 1KL", "2025", "1,750.00", "", ""),
    CommunityBuildingRow("4", "Name: John  Doe", "Address:  123\nLM:\nWillow school", "NE17 0FG", "2022", "3,890.00", "", ""),
    CommunityBuildingRow("500", "500th Building", "120 Backworth park", "NE17 0FG", "2024", "3,456.00", "", "")
  )

  val expectedResultOtherIncomeWithAttributes: Seq[OtherIncomeRow] = List(
    OtherIncomeRow("1", "Test           User\nOther", "01/01/25", "1,234.00", "56.00"),
    OtherIncomeRow("2", "Other income user", "28/02/24", "2,345.00", "87.00"),
    OtherIncomeRow("200", "200th User", "03/05/24", "6,000.00", "80.00")
  )

  val expectedResultConnectedCharitiesWithAttributes: Seq[ConnectedCharitiesRow] = List(
    ConnectedCharitiesRow("1", "Save the Children --- \nUK Trust", "CW789"),
    ConnectedCharitiesRow("4", "Test Charity", "CW123"),
    ConnectedCharitiesRow("4", "Test Charity", "CW123"),
    ConnectedCharitiesRow("4", "Test Charity", "CW123"),
    ConnectedCharitiesRow("4", "Test Charity", "CW123"),
    ConnectedCharitiesRow("200", "200th Charity", "CW777")
  )
}
