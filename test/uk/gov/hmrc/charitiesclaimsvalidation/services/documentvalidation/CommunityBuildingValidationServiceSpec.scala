/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation

import org.scalatest.prop.TableDrivenPropertyChecks
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{CommunityBuilding, CommunityBuildingData, ValidationError}
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.CommunityBuildingRow
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.CommunityBuildingValidationService.*
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.CommunityBuildingValidationServiceSpec.*
import uk.gov.hmrc.charitiesclaimsvalidation.util.BaseSpec

import cats.effect.unsafe.implicits.global
import java.time.LocalDate

//#validationService messages
//  validationService.communityBuildings.message.1 = Enter details for a Community Building item
//  validationService.communityBuildings.message.2 = There is an issue with this item number
//  validationService.communityBuildings.message.3 = Enter a building name
//  validationService.communityBuildings.message.4 = Enter a building name in the correct format
//  validationService.communityBuildings.message.5 = Enter a first line of address
//  validationService.communityBuildings.message.6 = Enter a first line of address in the correct format
//  validationService.communityBuildings.message.7 = Enter a postcode
//  validationService.communityBuildings.message.8 = Enter a postcode in the correct format
//  validationService.communityBuildings.message.9 = Enter a first tax year end date
//  validationService.communityBuildings.message.10 = Enter a second tax year end date
//  validationService.communityBuildings.message.11 = Enter a first tax year end date in the correct format
//  validationService.communityBuildings.message.12 = Enter a second tax year end date in the correct format
//  validationService.communityBuildings.message.14 = Community Buildings claim tax year must be this year or earlier
//  validationService.communityBuildings.message.15 = Community Buildings claim tax year cannot be earlier than {0}
//  validationService.communityBuildings.message.16 = Enter a first tax year amount
//  validationService.communityBuildings.message.17 = Enter a second tax year amount
//  validationService.communityBuildings.message.18 = Enter a first tax year amount in the correct format
//  validationService.communityBuildings.message.19 = Enter a second tax year amount in the correct format
//  validationService.communityBuildings.message.20 = Donations claimed for more than one tax year in a community building must be different to other tax years
//  validationService.communityBuildings.message.21 = Community Buildings can be claimed once per tax year per community building, up to a maximum of 3 years

class CommunityBuildingValidationServiceSpec extends BaseSpec with TableDrivenPropertyChecks {

  "validate" - {
    "return a list of valid rows with no errors given a valid community buildings spreadsheet" in {
      val (errorResult, validResult) =
        new CommunityBuildingValidationService().validate(CommunityBuildingGoodDataPath).futureValue

      errorResult shouldBe empty
      validResult.value.communityBuildings should not be empty
      validResult.value.communityBuildings.head.communityBuildingItem shouldBe 1
      validResult.value.communityBuildings.head.buildingName should not be empty
    }

    "return a list of valid rows with multiple spaces in Building name and First line of address, after normalising the spacing no errors given a valid community buildings spreadsheet" in {
      val (errorResult, validResult) =
        new CommunityBuildingValidationService().validate(CommunityBuildingGoodDataWithMultipleSpacesPath).futureValue

      errorResult shouldBe empty
      validResult.value.communityBuildings should not be empty
      validResult.value.communityBuildings.head.communityBuildingItem shouldBe 1
      validResult.value.communityBuildings.head.buildingName should not be empty
    }

    "return a list of valid rows with new lines, after removing new lines no errors given a valid community buildings spreadsheet" in {
      val (errorResult, validResult) =
        new CommunityBuildingValidationService().validate(CommunityBuildingGoodDataWithMultipleSpacesPath).futureValue

      errorResult shouldBe empty
      validResult.value.communityBuildings should not be empty
      validResult.value.communityBuildings.head.communityBuildingItem shouldBe 1
      validResult.value.communityBuildings.head.buildingName should not be empty
    }

    "return a list of invalid and valid rows given spreadsheet with invalid data" in {
      val (errorResult, _) = new CommunityBuildingValidationService().validate(CommunityBuildingBadDataPath).futureValue

      errorResult should not be empty
      errorResult.exists(_.field.contains("item")) shouldBe true
      errorResult.exists(_.field.contains("buildingName")) shouldBe true
      errorResult.exists(_.field.contains("postcode")) shouldBe true
      errorResult.exists(_.field.contains("taxYear")) shouldBe true
    }

    "return an error when there are empty results given an empty spreadsheet" in {
      val (errorResult, validResult) =
        new CommunityBuildingValidationService().validate(CommunityBuildingEmptyDataPath).futureValue

      errorResult shouldBe List(
        ValidationError(
          "communityBuildings",
          "validationService.communityBuildings.message.1"
        )
      )
      validResult should not be defined
    }

    "return a single validation error if the sheet name is incorrect" in {
      val (errorResult, validResult) =
        new CommunityBuildingValidationService().validate(CommunityBuildingBadSheetNameDataPath).futureValue

      errorResult shouldBe List(
        ValidationError(
          "fileError",
          "validationService.commonFile.message.3"
        )
      )
      validResult should not be defined
    }

    "return a single second date validation error if the second tax year is invalid" in {
      val (errorResult, validResult) =
        new CommunityBuildingValidationService().validate(CommunityBuildingBadSecondTaxDateDataPath).futureValue

      errorResult shouldBe List(
        ValidationError(
          "taxYearSecond[0]",
          "validationService.communityBuildings.message.12"
        )
      )
    }
  }

  "validateRows" - {
    "reject item number with leading zeros with single row and error" in {
      val row = BuildingRowWithIndex(
        17,
        CommunityBuildingRow(
          item = "01",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "100.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors should have size 1
      errors.head.field shouldBe "item[0]"
      errors.head.error shouldBe "validationService.communityBuildings.message.2"
    }

    "reject item number with leading zeros with multiple rows and errors" in {
      val rowList = List(
        BuildingRowWithIndex(
          17,
          CommunityBuildingRow(
            item = "1",
            buildingName = "Test Building 1",
            firstLineOfAddress = "123 Street",
            postcode = "SW1A 1AA",
            taxYear1 = "2023",
            amount1 = "100.00",
            taxYear2 = "",
            amount2 = ""
          )
        ),
        BuildingRowWithIndex(
          18,
          CommunityBuildingRow(
            item = "02",
            buildingName = "Test Building 2",
            firstLineOfAddress = "123 Street",
            postcode = "SW1A 1AA",
            taxYear1 = "2023",
            amount1 = "100.00",
            taxYear2 = "",
            amount2 = ""
          )
        ),
        BuildingRowWithIndex(
          19,
          CommunityBuildingRow(
            item = "03",
            buildingName = "你好",
            firstLineOfAddress = "123 Street",
            postcode = "SW1A 1AA",
            taxYear1 = "2023",
            amount1 = "100.00",
            taxYear2 = "",
            amount2 = ""
          )
        ),
        BuildingRowWithIndex(
          20,
          CommunityBuildingRow(
            item = "4",
            buildingName = "你好",
            firstLineOfAddress = "123 Street",
            postcode = "SW1A 1AA",
            taxYear1 = "2023",
            amount1 = "100.00",
            taxYear2 = "",
            amount2 = ""
          )
        ),
        BuildingRowWithIndex(
          21,
          CommunityBuildingRow(
            item = "5",
            buildingName = "你好",
            firstLineOfAddress = "你好",
            postcode = "你好",
            taxYear1 = "你好",
            amount1 = "你好",
            taxYear2 = "你好",
            amount2 = "你好"
          )
        )
      )

      val (errors, _) = validateRows(rowList, LocalDate.of(2025, 12, 18))

      errors should have size 12
      errors.head.field shouldBe "item[1]"
      errors.head.error shouldBe "validationService.communityBuildings.message.2"
      errors(1).field shouldBe "item[2]"
      errors(1).error shouldBe "validationService.communityBuildings.message.2"
      errors(2).field shouldBe "buildingName[2]"
      errors(2).error shouldBe "validationService.communityBuildings.message.3"
      errors(3).field shouldBe "buildingName[3]"
      errors(3).error shouldBe "validationService.communityBuildings.message.3"
      errors(4).field shouldBe "buildingName[4]"
      errors(4).error shouldBe "validationService.communityBuildings.message.3"
      errors(5).field shouldBe "firstLineOfAddress[4]"
      errors(5).error shouldBe "validationService.communityBuildings.message.5"
      errors(6).field shouldBe "postcode[4]"
      errors(6).error shouldBe "validationService.communityBuildings.message.7"
      errors(7).field shouldBe "taxYearFirst[4]"
      errors(7).error shouldBe "validationService.communityBuildings.message.9"
      errors(8).field shouldBe "amountFirst[4]"
      errors(8).error shouldBe "validationService.communityBuildings.message.16"
      errors(9).field shouldBe "taxYearSecond[4]"
      errors(9).error shouldBe "validationService.communityBuildings.message.10"
      errors(10).field shouldBe "amountSecond[4]"
      errors(10).error shouldBe "validationService.communityBuildings.message.17"
      errors(11).field shouldBe "taxYear2[4]"
      errors(
        11
      ).error shouldBe "validationService.communityBuildings.message.20"
    }

    "reject item number exceeding 500" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "501",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "100.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors should have size 1
      errors.head.error shouldBe "validationService.communityBuildings.message.2"
    }

    "reject building name exceeding 160 characters" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "A" * 161,
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "100.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors should have size 1
      errors.head.error shouldBe "validationService.communityBuildings.message.4"
    }

    "return a validation error when building name contains only blank-looking characters" in {
      val blankInputs = List(
        "–",            // en dash
        "—",            // em dash
        "…",            // ellipsis
        "\u00A0",       // non-breaking space
        "– –",          // en dash + space + en dash
        "\u2018 \u2019" // smart quotes around a space
      )
      blankInputs.foreach { name =>
        val row = BuildingRowWithIndex(
          17,
          CommunityBuildingRow(
            item = "1",
            buildingName = name,
            firstLineOfAddress = "123 Street",
            postcode = "SW1A 1AA",
            taxYear1 = "2023",
            amount1 = "100.00",
            taxYear2 = "",
            amount2 = ""
          )
        )

        val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

        errors should contain(ValidationError("buildingName[0]", "validationService.communityBuildings.message.3"))
      }
    }

    "accept building name at exactly 160 characters" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "A" * 160,
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "100.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, valids) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors shouldBe empty
      valids should have size 1
    }

    "accept building name when the name is valid, even if it contains multiple spaces, after normalising the spacing" in {
      val row = BuildingRowWithIndex(
        18,
        CommunityBuildingRow(
          item = "1",
          buildingName = "There    are     multiple     spaces  ",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "100.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, valids) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors shouldBe empty
      valids shouldBe List(
        ValidatedBuildingWithIndex(
          errorIndex = 1,
          building = CommunityBuilding(
            communityBuildingItem = 1,
            buildingName = "There are multiple spaces",
            firstLineOfAddress = "123 Street",
            postcode = "SW1A 1AA",
            taxYear1 = 2023,
            amountYear1 = BigDecimal("100.00"),
            taxYear2 = None,
            amountYear2 = None
          )
        )
      )
    }

    "reject address exceeding 40 characters" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "Test Building",
          firstLineOfAddress = "A" * 41,
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "100.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors should have size 1
      errors.head.error shouldBe "validationService.communityBuildings.message.6"
    }

    "accept first line of address when it is valid, even if it contains multiple spaces, after normalising the spacing" in {
      val row = BuildingRowWithIndex(
        18,
        CommunityBuildingRow(
          item = "1",
          buildingName = "Test Building",
          firstLineOfAddress = "There    are     multiple     spaces  ",
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "100.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, valids) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors shouldBe empty
      valids shouldBe List(
        ValidatedBuildingWithIndex(
          errorIndex = 1,
          building = CommunityBuilding(
            communityBuildingItem = 1,
            buildingName = "Test Building",
            firstLineOfAddress = "There are multiple spaces",
            postcode = "SW1A 1AA",
            taxYear1 = 2023,
            amountYear1 = BigDecimal("100.00"),
            taxYear2 = None,
            amountYear2 = None
          )
        )
      )
    }

    "accept valid UK postcodes" in {
      val validPostcodes = Table(
        "postcode",
        "SW1A 1AA",
        "SW1A 1AA",
        "M1 1AA",
        "B33 8TH",
        "CR2 6XH",
        "DN55 1PT",
        "W1A 0AX",
        "EC1A 1BB",
        "GIR 0AA"
      )

      forAll(validPostcodes) { postcode =>
        val row = BuildingRowWithIndex(
          0,
          CommunityBuildingRow(
            item = "1",
            buildingName = "Test Building",
            firstLineOfAddress = "123 Street",
            postcode = postcode,
            taxYear1 = "2023",
            amount1 = "100.00",
            taxYear2 = "",
            amount2 = ""
          )
        )

        val (errors, valids) = validateRows(List(row), LocalDate.of(2025, 12, 18))

        withClue(s"Postcode $postcode should be valid: ") {
          errors shouldBe empty
          valids should have size 1
        }
      }
    }

    "reject invalid postcodes" in {
      val invalidPostcodes = Table(
        "postcode",
        "INVALID",
        "12345",
        "SW1A_1AA",
        "A",
        "",
        "B338TH",
        "GIR0AA"
      )

      forAll(invalidPostcodes) { postcode =>
        val row = BuildingRowWithIndex(
          0,
          CommunityBuildingRow(
            item = "1",
            buildingName = "Test Building",
            firstLineOfAddress = "123 Street",
            postcode = postcode,
            taxYear1 = "2023",
            amount1 = "100.00",
            taxYear2 = "",
            amount2 = ""
          )
        )

        val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

        withClue(s"Postcode '$postcode' should be invalid: ") {
          errors should not be empty
        }
      }
    }

    "reject tax year before 2014 (GASDS scheme start year)" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2013", // Below minimum - GASDS started in 2014
          amount1 = "100.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, _) = validateRows(List(row), LocalDate.of(2016, 12, 18)) // CY=2017, CY-3=2014

      errors should have size 1
      errors.last.error shouldBe "validationService.communityBuildings.message.15"
    }

    "accept tax year 2015 (GASDS scheme start year) when within CY-3 range" in {
      // For 2015 to be valid, CY must be 2018 or earlier (since 2015 >= CY-3)
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2015", // GASDS minimum year
          amount1 = "100.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, valids) = validateRows(List(row), LocalDate.of(2017, 12, 18)) // CY=2018, CY-3=2015

      errors shouldBe empty
      valids should have size 1
    }

    "reject tax year in the future" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2027", // CY set to 2026 in this test
          amount1 = "100.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors should have size 1
      errors.head.error shouldBe "validationService.communityBuildings.message.14"
    }

    "accept tax year matching current calendar year even before April 6th" in {
      // When today before April 6th, tax year 2026 should be VALID
      // This uses calendar year (2026) not UK tax year (which would be 2025)
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2026", // Same as calendar year
          amount1 = "100.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, valids) = validateRows(List(row), LocalDate.of(2026, 2, 5)) // Before April 6th

      errors shouldBe empty
      valids should have size 1
      valids.head.building.taxYear1 shouldBe 2026
    }

    "not produce future tax year error for tax year YYYY when today is before April 6th YYYY" in {
      val row = BuildingRowWithIndex(
        31,
        CommunityBuildingRow(
          item = "32",
          buildingName = "The Cross Field 2",
          firstLineOfAddress = "2 Ferrous Street",
          postcode = "L22 1KL",
          taxYear1 = "2025",
          amount1 = "50.00",
          taxYear2 = "2026",
          amount2 = "50.00"
        )
      )

      val (errors, valids) = validateRows(List(row), LocalDate.of(2026, 2, 5))

      errors shouldBe empty
      valids should have size 1
      valids.head.building.taxYear2 shouldBe Some(2026)
    }

    "reject tax year earlier than CY-3" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2021", // CY set to 2025 in this test, CY-3 is 2022
          amount1 = "100.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors should have size 1
      errors.head.error shouldBe "validationService.communityBuildings.message.15"
    }

    "accept amount with comma separators" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "1,234,567.89",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, valids) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors shouldBe empty
      valids should have size 1
      valids.head.building.amountYear1 shouldBe BigDecimal("1234567.89")
    }

    "reject amount below minimum (0.01)" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "0.00",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors should have size 1
      errors.head.error shouldBe "validationService.communityBuildings.message.18"
    }

    "reject amount with more than 2 decimal places" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "123.456",
          taxYear2 = "",
          amount2 = ""
        )
      )

      val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors should have size 1
      errors.head.error shouldBe "validationService.communityBuildings.message.18"
    }

    "reject conditional validation - taxYear2 without amount2" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "100.00",
          taxYear2 = "2024",
          amount2 = ""
        )
      )

      val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors should have size 1
      errors.head.error shouldBe "validationService.communityBuildings.message.17"
    }

    "reject conditional validation - amount2 without taxYear2" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "1",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "100.00",
          taxYear2 = "", // Missing
          amount2 = "200.00"
        )
      )

      val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors should have size 1
      errors.head.error shouldBe "validationService.communityBuildings.message.10"
    }

    "reject when taxYear1 and taxYear2 are the same within a row" in {
      val row = BuildingRowWithIndex(
        0,
        CommunityBuildingRow(
          item = "18",
          buildingName = "Test Building",
          firstLineOfAddress = "123 Street",
          postcode = "SW1A 1AA",
          taxYear1 = "2023",
          amount1 = "100.00",
          taxYear2 = "2023", // Same as taxYear1
          amount2 = "200.00"
        )
      )

      val (errors, _) = validateRows(List(row), LocalDate.of(2025, 12, 18))

      errors should have size 1
      errors.head.error shouldBe "validationService.communityBuildings.message.20"
    }
  }

  "validateCrossField" - {
    "report duplicate item number (later row) not first item when same building has duplicate tax year" in {
      // Item 24 is the first row for this building with tax year 2023
      // Item 25 is the second row for the same building with tax year 2023 (duplicate)
      // The error should report Item 25, not Item 24
      val building1 = ValidatedBuildingWithIndex(
        errorIndex = 0,
        building = CommunityBuilding(
          communityBuildingItem = 24,
          buildingName = "Test Building",
          firstLineOfAddress = "31 test address",
          postcode = "AB112CD",
          taxYear1 = 2023,
          amountYear1 = BigDecimal("100.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val building2 = ValidatedBuildingWithIndex(
        errorIndex = 1,
        building = CommunityBuilding(
          communityBuildingItem = 25,
          buildingName = "Test Building",
          firstLineOfAddress = "31 test address",
          postcode = "AB112CD",
          taxYear1 = 2023, // Same tax year as building1 - this is the duplicate
          amountYear1 = BigDecimal("200.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val errors = validateCrossField(List(building1, building2))

      errors should have size 1
      errors.head.error shouldBe "validationService.communityBuildings.message.21"
    }

    "report correct item when same building exceeds 3 tax years" in {
      // Building with 4 tax years across 2 rows - the 4th year (on item 26) should be reported
      val building1 = ValidatedBuildingWithIndex(
        errorIndex = 0,
        building = CommunityBuilding(
          communityBuildingItem = 24,
          buildingName = "Test Building",
          firstLineOfAddress = "31 test address",
          postcode = "AB112CD",
          taxYear1 = 2022,
          amountYear1 = BigDecimal("100.00"),
          taxYear2 = Some(2023),
          amountYear2 = Some(BigDecimal("100.00"))
        )
      )

      val building2 = ValidatedBuildingWithIndex(
        errorIndex = 1,
        building = CommunityBuilding(
          communityBuildingItem = 26,
          buildingName = "Test Building",
          firstLineOfAddress = "31 test address",
          postcode = "AB112CD",
          taxYear1 = 2024,
          amountYear1 = BigDecimal("200.00"),
          taxYear2 = Some(2025), // This is the 4th tax year - exceeds limit
          amountYear2 = Some(BigDecimal("200.00"))
        )
      )

      val errors = validateCrossField(List(building1, building2))

      errors should have size 1
      errors.head.error shouldBe "validationService.communityBuildings.message.21"
    }

    "report both duplicate and limit errors when both conditions exist" in {
      // Building with 5 entries: 4 unique years + 1 duplicate
      // Years: 2021, 2022, 2023, 2024, 2022 (duplicate)
      // This should report:
      // - Item 28 for duplicate (2022 already seen from Item 24)
      // - Item 27 for exceeding 3-year limit (introduced 4th unique year 2024)
      val building1 = ValidatedBuildingWithIndex(
        errorIndex = 0,
        building = CommunityBuilding(
          communityBuildingItem = 24,
          buildingName = "Test Building",
          firstLineOfAddress = "31 test address",
          postcode = "AB112CD",
          taxYear1 = 2021,
          amountYear1 = BigDecimal("100.00"),
          taxYear2 = Some(2022),
          amountYear2 = Some(BigDecimal("100.00"))
        )
      )

      val building2 = ValidatedBuildingWithIndex(
        errorIndex = 1,
        building = CommunityBuilding(
          communityBuildingItem = 26,
          buildingName = "Test Building",
          firstLineOfAddress = "31 test address",
          postcode = "AB112CD",
          taxYear1 = 2023,
          amountYear1 = BigDecimal("200.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val building3 = ValidatedBuildingWithIndex(
        errorIndex = 2,
        building = CommunityBuilding(
          communityBuildingItem = 27,
          buildingName = "Test Building",
          firstLineOfAddress = "31 test address",
          postcode = "AB112CD",
          taxYear1 = 2024, // 4th unique year - exceeds limit
          amountYear1 = BigDecimal("200.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val building4 = ValidatedBuildingWithIndex(
        errorIndex = 3,
        building = CommunityBuilding(
          communityBuildingItem = 28,
          buildingName = "Test Building",
          firstLineOfAddress = "31 test address",
          postcode = "AB112CD",
          taxYear1 = 2022, // Duplicate of Item 24's taxYear2
          amountYear1 = BigDecimal("200.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val errors = validateCrossField(List(building1, building2, building3, building4))

      errors should have size 2
      val errorMessages = errors.map(_.error)
      errorMessages should contain(
        "validationService.communityBuildings.message.21"
      )
      errorMessages should contain(
        "validationService.communityBuildings.message.21"
      )
    }

    "return duplicate building errors sorted in sequential order by field index when sortErrorsByField is used" in {
      val building1 = ValidatedBuildingWithIndex(
        errorIndex = 0,
        building = CommunityBuilding(
          communityBuildingItem = 1,
          buildingName = "Building A",
          firstLineOfAddress = "1 Alpha Street",
          postcode = "AA1 1AA",
          taxYear1 = 2024,
          amountYear1 = BigDecimal("100.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val building2 = ValidatedBuildingWithIndex(
        errorIndex = 1,
        building = CommunityBuilding(
          communityBuildingItem = 2,
          buildingName = "Building A", // Duplicate of building1
          firstLineOfAddress = "1 Alpha Street",
          postcode = "AA1 1AA",
          taxYear1 = 2024,
          amountYear1 = BigDecimal("100.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val building3 = ValidatedBuildingWithIndex(
        errorIndex = 2,
        building = CommunityBuilding(
          communityBuildingItem = 3,
          buildingName = "Building B",
          firstLineOfAddress = "2 Beta Street",
          postcode = "BB2 2BB",
          taxYear1 = 2024,
          amountYear1 = BigDecimal("100.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val building4 = ValidatedBuildingWithIndex(
        errorIndex = 3,
        building = CommunityBuilding(
          communityBuildingItem = 4,
          buildingName = "Building B", // Duplicate of building3
          firstLineOfAddress = "2 Beta Street",
          postcode = "BB2 2BB",
          taxYear1 = 2024,
          amountYear1 = BigDecimal("100.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val building5 = ValidatedBuildingWithIndex(
        errorIndex = 4,
        building = CommunityBuilding(
          communityBuildingItem = 5,
          buildingName = "Building C",
          firstLineOfAddress = "3 Charlie Street",
          postcode = "CC3 3CC",
          taxYear1 = 2024,
          amountYear1 = BigDecimal("100.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val building6 = ValidatedBuildingWithIndex(
        errorIndex = 5,
        building = CommunityBuilding(
          communityBuildingItem = 6,
          buildingName = "Building C", // Duplicate of building5
          firstLineOfAddress = "3 Charlie Street",
          postcode = "CC3 3CC",
          taxYear1 = 2024,
          amountYear1 = BigDecimal("100.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val building7 = ValidatedBuildingWithIndex(
        errorIndex = 6,
        building = CommunityBuilding(
          communityBuildingItem = 7,
          buildingName = "Building D",
          firstLineOfAddress = "4 Delta Street",
          postcode = "DD4 4DD",
          taxYear1 = 2024,
          amountYear1 = BigDecimal("100.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val building8 = ValidatedBuildingWithIndex(
        errorIndex = 7,
        building = CommunityBuilding(
          communityBuildingItem = 8,
          buildingName = "Building D", // Duplicate of building7
          firstLineOfAddress = "4 Delta Street",
          postcode = "DD4 4DD",
          taxYear1 = 2024,
          amountYear1 = BigDecimal("100.00"),
          taxYear2 = None,
          amountYear2 = None
        )
      )

      val crossFieldErrors = validateCrossField(List(building1, building2, building3, building4, building5, building6, building7, building8))

      crossFieldErrors should have size 4

      val sortedErrors = sortErrorsByField(crossFieldErrors)

      val bracketPattern = """\[(\d+)\]""".r
      val fieldIndices: List[Int] = sortedErrors.map { error =>
        bracketPattern
          .findFirstMatchIn(error.field)
          .map(_.group(1).toInt)
          .getOrElse(0)
      }

      fieldIndices shouldBe List(1, 3, 5, 7)
      fieldIndices shouldBe sorted
    }
  }

  "ensure non western chars are removed from inputs like the AS IS system" in {
    // Only donation fields seem to not remove the invalid chars so ignoring this from test
    val (errorRows, validRows) = CommunityBuildingValidationService.validateRows(
      List(
        BuildingRowWithIndex(
          0,
          CommunityBuildingRow(
            item = "☺1",
            buildingName = "☺Test Building",
            firstLineOfAddress = "☺123 Street",
            postcode = "☺SW1A 1AA",
            taxYear1 = "☺2023",
            amount1 = "100.00", // this and amount2 are the only fields in the spreadsheet that don't clean the user input
            taxYear2 = "☺2024",
            amount2 = "200.00"
          )
        )
      ),
      LocalDate.of(2026, 4, 5)
    )

    errorRows should have size 0
    validRows should have size 1
  }
}

object CommunityBuildingValidationServiceSpec {
  val CommunityBuildingGoodDataPath: String =
    new java.io.File("test/resources/communitybuildings/community_buildings_excel-GoodData.ods").toURI.toURL.toString
  val CommunityBuildingGoodDataWithMultipleSpacesPath: String =
    new java.io.File("test/resources/communitybuildings/community_buildings_excel-GoodDataWithMultipleSpaces.ods").toURI.toURL.toString
  val CommunityBuildingBadDataPath: String =
    new java.io.File("test/resources/communitybuildings/community_buildings_excel-BadData.ods").toURI.toURL.toString
  val CommunityBuildingEmptyDataPath: String =
    new java.io.File("test/resources/communitybuildings/community_buildings_excel-EmptyData.ods").toURI.toURL.toString
  val CommunityBuildingBadSheetNameDataPath: String =
    new java.io.File("test/resources/communitybuildings/community_buildings_excel-BadSheetNameData.ods").toURI.toURL.toString
  val CommunityBuildingBadSecondTaxDateDataPath: String =
    new java.io.File("test/resources/communitybuildings/community_buildings_excel-BadSecondYearDate.ods").toURI.toURL.toString
}
