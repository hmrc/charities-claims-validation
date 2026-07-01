/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation

import cats.effect.unsafe.implicits.global
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{GiftAidDonation, GiftAidScheduleData, ValidationError}
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.GiftAidDonationRow
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.GiftAidValidationService.GiftAidDonationRowWithIndex
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.GiftAidValidationServiceSpec.*
import uk.gov.hmrc.charitiesclaimsvalidation.util.BaseSpec

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
validationService.giftAid.message.1 = Enter details for at least one Gift Aid donation
validationService.giftAid.message.2 = Enter the earliest donation date
validationService.giftAid.message.3 = Enter the earliest donation date in the correct format
validationService.giftAid.message.4 = Enter a previously overclaimed amount in the correct format
validationService.giftAid.message.5 = Enter an aggregated donations entry in the correct format
validationService.giftAid.message.6 = There is an issue with this item number
validationService.giftAid.message.7 = Enter a donation date
validationService.giftAid.message.8 = Enter a donation date in the correct format
validationService.giftAid.message.9 = Gift Aid donation date cannot be in the future
validationService.giftAid.message.10 = Enter a donation amount in the correct format
validationService.giftAid.message.11 = Donation amount is missing
validationService.giftAid.message.12 = Enter an aggregated donation amount that is £1000 or smaller
validationService.giftAid.message.13 = Enter a donor title in the correct format
validationService.giftAid.message.14 = Enter a donor first name
validationService.giftAid.message.15 = Enter a donor last name
validationService.giftAid.message.16 = Enter a donor first name in the correct format
validationService.giftAid.message.17 = Enter a donor last name in the correct format
validationService.giftAid.message.18 = Enter a donor house name or number
validationService.giftAid.message.19 = Enter a donor house name or number in the correct format
validationService.giftAid.message.20 = Enter a donor postcode
validationService.giftAid.message.21 = Enter a donor postcode in the correct format
validationService.giftAid.message.22 = Enter ‘Yes’ if this is a sponsored event, otherwise leave blank
validationService.giftAid.message.23 = The selected field must be an aggregated donation, sponsored event or ordinary donation
validationService.giftAid.message.24 = You cannot provide a title for an aggregated donation
validationService.giftAid.message.25 = You cannot provide a first name for an aggregated donation
validationService.giftAid.message.26 = You cannot provide a last name for an aggregated donation
validationService.giftAid.message.27 = You cannot provide a house name or number for an aggregated donation
validationService.giftAid.message.28 = You cannot provide a postcode for an aggregated donation
 */

class GiftAidValidationServiceSpec extends BaseSpec {

  "validate" - {
    "return a list of valid rows with no errors given a valid gift aid donations spreadsheet" in {
      val (errorResult, validResult) = new GiftAidValidationService().validate(giftAidGoodDataPath).futureValue

      errorResult shouldBe empty
      validResult.value shouldBe validData
    }

    "return a list of valid rows with multiple spaces in First names, House name or number no and Aggregate Donations, after normalising the spacing no errors given a valid gift aid donations spreadsheet" in {
      val (errorResult, validResult) = new GiftAidValidationService().validate(giftAidGoodDataWithMultipleSpacesPath).futureValue

      errorResult shouldBe empty
      validResult.value shouldBe validDataWithMultipleSpaces
    }

    "return a list of valid rows with new lines, after removing new lines no errors given a valid gift aid donations spreadsheet" in {
      val (errorResult, validResult) = new GiftAidValidationService().validate(giftAidGoodDataWithMultipleSpacesPath).futureValue

      errorResult shouldBe empty
      validResult.value shouldBe validDataWithMultipleSpaces
    }

    "return a errors when the cells have invalid data given a bad data gift aid donations spreadsheet" in {
      val (errorResult, validResult) = new GiftAidValidationService().validate(earliestDonationDateBadDataPath).futureValue

      errorResult shouldBe BadEarliestDateOverClaimAmount
      validResult.value shouldBe validRowsNoEarliestDonationDate
    }

    "return a list of invalid rows given spreadsheet with invalid data" in {
      val (errorResult, validResult) = new GiftAidValidationService().validate(giftAidBadDataPath).futureValue

      errorResult shouldBe BadDataValidationErrors
      validResult shouldBe None
    }

    "return no errors and no valid rows when spreadsheet is empty" in {
      val (errorResult, validResult) = new GiftAidValidationService().validate(giftAidEmptyDataPath).futureValue

      errorResult shouldBe List(
        ValidationError(
          "giftAidDonation",
          "validationService.giftAid.message.1"
        )
      )
      validResult should not be defined
    }

    "return a single validation error if the sheet name is incorrect" in {
      val (errorResult, validResult) = new GiftAidValidationService().validate(giftAidBadSheetNameDataPath).futureValue

      errorResult shouldBe List(
        ValidationError(
          "fileError",
          "validationService.commonFile.message.3"
        )
      )
      validResult shouldBe None
    }
  }

  "validateRows " - {

    "validate item values" - {

      val itemError = ValidationError(
        "item[0]",
        "validationService.giftAid.message.6"
      )

      "return no errors when item is between 1 and 1000" in {
        List("1", "1000", " 5 ").foreach { item =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(
                0,
                GiftAidDonationRow(item, "Prof", "Henry", "House Martin", "152A", "M99 2QD", "", "", "24/06/15", "240.00")
              )
            )
          )

          errorRows shouldBe empty
          validRows should have size 1
        }
      }

      "return a validation error when item is out of range, non-numeric, or padded with zeros" in {
        List("0", "1234", "01", "abc", "").foreach { item =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(
                0,
                GiftAidDonationRow(item, "Prof", "Henry", "House Martin", "152A", "M99 2QD", "", "", "24/06/15", "240.00")
              )
            )
          )

          errorRows should contain(itemError)
          validRows shouldBe empty
        }
      }
    }

    "validate title" - {

      val titleInvalidError =
        ValidationError(
          "donorTitle[0]",
          "validationService.giftAid.message.13"
        )

      "return no errors when title is valid" in {
        List("Mr", "Prof", "Ms", "Miss").foreach { title =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", title, "Henry", "House Martin", "152A", "M99 2QD", "", "", "24/06/15", "240.00"))
            )
          )

          errorRows shouldBe empty
          validRows should have size 1
        }
      }

      "return a validation error when the title is not in valid format" in {
        List("123", "Mister", "Mr.").foreach { title =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", title, "Henry", "House Martin", "152A", "M99 2QD", "", "", "24/06/15", "240.00"))
            )
          )

          errorRows should contain(titleInvalidError)
          validRows shouldBe empty
        }
      }
    }

    "validate name" - {

      val nameInvalidError =
        ValidationError(
          "donorFirstName[0]",
          "validationService.giftAid.message.16"
        )

      "return no errors when name is valid" in {
        List("Test123", "O'Test", "Company_Name", "12345678901234567890123456789012345").foreach { name =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "Prof", name, "House Martin", "152A", "M99 2QD", "", "", "24/06/15", "240.00"))
            )
          )

          errorRows shouldBe empty
          validRows should have size 1
        }
      }

      "return a valid row when the firstname is valid, even if it contains multiple spaces, after normalising the spacing" in {
        val (errorRows, validRows) = GiftAidValidationService.validateRows(
          List(
            GiftAidDonationRowWithIndex(
              0,
              GiftAidDonationRow("1", "Prof", "Henry     Spaces  1", "House Martin", "152A", "M99 2QD", "", "", "24/06/15", "240.00")
            )
          )
        )

        errorRows shouldBe empty
        validRows shouldBe List(
          GiftAidDonation(
            1,
            Some("Prof"),
            Some("Henry Spaces 1"),
            Some("House Martin"),
            Some("152A"),
            Some("M99 2QD"),
            None,
            false,
            LocalDate.of(2015, 6, 24),
            240.00
          )
        )
      }

      "return a valid row when the lastname is valid, even if it contains multiple spaces, after normalising the spacing" in {
        val (errorRows, validRows) = GiftAidValidationService.validateRows(
          List(
            GiftAidDonationRowWithIndex(
              0,
              GiftAidDonationRow("1", "Prof", "Henry", "House    Martin   spaces", "152A", "M99 2QD", "", "", "24/06/15", "240.00")
            )
          )
        )

        errorRows shouldBe empty
        validRows shouldBe List(
          GiftAidDonation(
            1,
            Some("Prof"),
            Some("Henry"),
            Some("House Martin spaces"),
            Some("152A"),
            Some("M99 2QD"),
            None,
            false,
            LocalDate.of(2015, 6, 24),
            240.00
          )
        )
      }

      "return a validation error when the name is not in valid format" in {
        List("name is longer than thirty five characters").foreach { name =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "Prof", name, "House Martin", "152A", "M99 2QD", "", "", "24/06/15", "240.00"))
            )
          )

          errorRows should contain(nameInvalidError)
          validRows shouldBe empty
        }
      }

      "return a validation error when the name contains only blank-looking characters" in {
        val blankInputs = List(
          "–",            // en dash
          "—",            // em dash
          "…",            // ellipsis
          "\u00A0",       // non-breaking space
          "– –",          // en dash + space + en dash
          "\u2018 \u2019" // smart quotes around a space
        )
        blankInputs.foreach { name =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "Prof", name, "House Martin", "152A", "M99 2QD", "", "", "24/06/15", "240.00"))
            )
          )

          errorRows should contain(ValidationError("donorFirstName[0]", "validationService.giftAid.message.14"))
          validRows shouldBe empty
        }
      }
    }

    "validate house name" - {

      val houseNameInvalidError =
        ValidationError(
          "donorHouse[0]",
          "validationService.giftAid.message.19"
        )

      "return no errors when house name is valid" in {
        List("12 A", "221B Baker Street", "No. 45").foreach { house =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "Prof", "Henry", "House Martin", house, "M99 2QD", "", "", "24/06/15", "240.00"))
            )
          )

          errorRows shouldBe empty
          validRows should have size 1
        }
      }

      "return a valid row when the house is valid, even if it contains multiple spaces, after normalising the spacing" in {
        val (errorRows, validRows) = GiftAidValidationService.validateRows(
          List(
            GiftAidDonationRowWithIndex(
              0,
              GiftAidDonationRow("1", "Prof", "Henry", "House   Martin", "152A      house   no", "M99 2QD", "", "", "24/06/15", "240.00")
            )
          )
        )

        errorRows shouldBe empty
        validRows shouldBe List(
          GiftAidDonation(
            1,
            Some("Prof"),
            Some("Henry"),
            Some("House Martin"),
            Some("152A house no"),
            Some("M99 2QD"),
            None,
            false,
            LocalDate.of(2015, 6, 24),
            240.00
          )
        )
      }

      "return a validation error when the house name is not in valid format" in {
        List("house name is longer than forty characters").foreach { house =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "Prof", "Henry", "House Martin", house, "M99 2QD", "", "", "24/06/15", "240.00"))
            )
          )

          errorRows should contain(houseNameInvalidError)
          validRows shouldBe empty
        }
      }
    }

    "validate postcode" - {

      val postCodeInvalidError =
        ValidationError(
          "postcode[0]",
          "validationService.giftAid.message.21"
        )

      "return no errors when postcode is valid or X" in {
        List("X", "EC1A 1BB", "W1O 7HG", "SW1A 1AA").foreach { postcode =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "Prof", "Henry", "House Martin", "152A", postcode, "", "", "24/06/15", "240.00"))
            )
          )

          errorRows shouldBe empty
          validRows should have size 1
        }
      }

      "return a validation error when the postcode is not in valid format" in {
        List("12345", "SW1 1A", "ABCDE", "EC1A 1A1", "NE270QQ", "x").foreach { postcode =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "Prof", "Henry", "House Martin", "152A", postcode, "", "", "24/06/15", "240.00"))
            )
          )

          errorRows should contain(postCodeInvalidError)
          validRows shouldBe empty
        }
      }
    }

    "validate sponsored event" - {

      val sponsoredEventInvalidError =
        ValidationError(
          "sponsoredEvent[0]",
          "validationService.giftAid.message.22"
        )

      "return no errors when sponsored event is valid" in {
        List("yes", "Yes", "").foreach { event =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(
                0,
                GiftAidDonationRow("1", "Prof", "Henry", "House Martin", "152A", "M99 2QD", "", event, "24/06/15", "240.00")
              )
            )
          )

          errorRows shouldBe empty
          validRows should have size 1
        }
      }

      "return a validation error when the sponsored event is not in valid format" in {
        List("No", "no", "False", "1").foreach { event =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(
                0,
                GiftAidDonationRow("1", "Prof", "Henry", "House Martin", "152A", "M99 2QD", "", event, "24/06/15", "240.00")
              )
            )
          )

          errorRows should contain(sponsoredEventInvalidError)
          validRows shouldBe empty
        }
      }
    }

    "validate money" - {

      val amountInvalidError =
        ValidationError(
          "donationAmount[0]",
          "validationService.giftAid.message.10"
        )
      val aggregationAmountInvalidError =
        ValidationError(
          "donationAmount[0]",
          "validationService.giftAid.message.12"
        )

      "return no errors when amount is valid for all donation types" in {
        List("0.01", "9999999999999.99", "1,000.00").foreach { amount =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "Prof", "Henry", "House Martin", "152A", "M99 2QD", "", "", "24/06/15", amount))
            )
          )

          errorRows shouldBe empty
          validRows should have size 1
        }
      }

      "return a validation error when the amount is not in valid format" in {
        List("abc", "£10.00", "10.000", "--10.00", "100000000000000.00", "99999999999999.99", "-1.00").foreach { amount =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "Prof", "Henry", "House Martin", "152A", "M99 2QD", "", "", "24/06/15", amount))
            )
          )

          errorRows should contain(amountInvalidError)
          validRows shouldBe empty
        }
      }

      "return a validation error when the amount is more than 1000 for aggregated donation type" in {
        List("1000.01", "9999.99").foreach { amount =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "", "", "", "", "", "Yes", "", "24/06/15", amount))
            )
          )

          errorRows should contain(aggregationAmountInvalidError)
          validRows shouldBe empty
        }
      }
    }

    "validate date" - {

      val dateInvalidError =
        ValidationError(
          "donationDate[0]",
          "validationService.giftAid.message.8"
        )

      val dateInFutureError =
        ValidationError(
          "donationDate[0]",
          "validationService.giftAid.message.9"
        )

      "return no errors when donation date is valid" in {
        List("31/12/25", "29/02/24", "11/01/26", "28/02/25").foreach { date =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "Prof", "Henry", "House Martin", "152A", "M99 2QD", "", "", date, "240.00"))
            )
          )

          errorRows shouldBe empty
          validRows should have size 1
        }
      }

      "return a validation error when the donation date is not in valid format" in {
        List("2026-01-12", "12/01/2026", "12-01-26", "30/02/25", "29/02/25").foreach { date =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "Prof", "Henry", "House Martin", "152A", "M99 2QD", "", "", date, "240.00"))
            )
          )

          errorRows should contain(dateInvalidError)
          validRows shouldBe empty
        }
      }

      "return a validation error when the donation date is in future" in {
        val formatter  = DateTimeFormatter.ofPattern("dd/MM/yy")
        val futureDate = LocalDate.now().plusDays(1).format(formatter)
        List(futureDate).foreach { date =>
          val (errorRows, validRows) = GiftAidValidationService.validateRows(
            List(
              GiftAidDonationRowWithIndex(0, GiftAidDonationRow("1", "Prof", "Henry", "House Martin", "152A", "M99 2QD", "", "", date, "240.00"))
            )
          )

          errorRows should contain(dateInFutureError)
          validRows shouldBe empty
        }
      }

      "checking fields that remove non western characters in the AS IS replicate behaviour" in {
        val (errorRows, validRows) = GiftAidValidationService.validateRows(
          List(
            GiftAidDonationRowWithIndex(
              0,
              GiftAidDonationRow("☺1", "☺Mr", "☺First Name", "☺Last Name", "☺House Number", "☺M99 2QD", "☺", "☺", "☺24/06/15", "☺240.00")
            )
          )
        )

        errorRows should have size 0
        validRows should have size 1
      }

      "checking fields that remove non western characters in the AS IS replicate behaviour (aggregated donation field)" in {
        val (errorRows, validRows) = GiftAidValidationService.validateRows(
          List(
            GiftAidDonationRowWithIndex(
              0,
              GiftAidDonationRow("1", "", "", "", "", "", "This is a tes☺t", "", "01/01/25", "100.00")
            )
          )
        )

        errorRows should have size 0
        validRows should have size 1
      }
    }
  }
}

object GiftAidValidationServiceSpec {
  val giftAidGoodDataPath: String =
    new java.io.File("test/resources/giftAid/Gift-Aid-Schedule-Excel-GoodData.ods").toURI.toURL.toString
  val giftAidGoodDataWithMultipleSpacesPath: String =
    new java.io.File("test/resources/giftAid/Gift-Aid-Schedule-Excel-GoodDataWithMultipleSpaces.ods").toURI.toURL.toString
  val giftAidBadDataPath: String =
    new java.io.File("test/resources/giftAid/Gift-Aid-Schedule-Excel-BadData.ods").toURI.toURL.toString
  val earliestDonationDateBadDataPath: String =
    new java.io.File("test/resources/giftAid/Gift-Aid-Schedule-Excel-InvalidEarliestDonationDate.ods").toURI.toURL.toString
  val giftAidEmptyDataPath: String =
    new java.io.File("test/resources/giftAid/Gift-Aid-Schedule-Excel-EmptyData.ods").toURI.toURL.toString
  val giftAidBadSheetNameDataPath: String =
    new java.io.File("test/resources/giftAid/Gift-Aid-Schedule-Excel-BadSheetNameData.ods").toURI.toURL.toString

  val validData = GiftAidScheduleData(
    Some(LocalDate.of(2017, 11, 10)),
    None,
    Some(1450.00),
    List(
      GiftAidDonation(
        1,
        Some("Prof"),
        Some("Henry"),
        Some("House Martin"),
        Some("152A"),
        Some("M99 2QD"),
        None,
        false,
        LocalDate.of(2015, 3, 24),
        240.00
      ),
      GiftAidDonation(
        2,
        Some("Mr"),
        Some("John"),
        Some("Smith"),
        Some("100 Champs Elysees, Paris"),
        Some("X"),
        None,
        false,
        LocalDate.of(2015, 6, 24),
        250.00
      ),
      GiftAidDonation(3, None, None, None, None, None, Some("One off Gift Aid donations"), false, LocalDate.of(2015, 3, 31), 880.00),
      GiftAidDonation(4, Some("Miss"), Some("B"), Some("Chaudry"), Some("21"), Some("L43 4FB"), None, true, LocalDate.of(2015, 4, 26), 80.00)
    )
  )

  val validDataWithMultipleSpaces = GiftAidScheduleData(
    Some(LocalDate.of(2017, 11, 10)),
    None,
    Some(1700.00),
    List(
      GiftAidDonation(
        1,
        Some("Prof"),
        Some("Henry Multiple Spaces"),
        Some("House Martin"),
        Some("152A Multiple Spaces"),
        Some("M99 2QD"),
        None,
        false,
        LocalDate.of(2015, 3, 24),
        240.00
      ),
      GiftAidDonation(
        2,
        Some("Mr"),
        Some("John Multiple Spaces"),
        Some("Smith Multiple Spaces"),
        Some("100 Champs Elysees, Paris"),
        Some("X"),
        None,
        false,
        LocalDate.of(2015, 6, 24),
        250.00
      ),
      GiftAidDonation(3, None, None, None, None, None, Some("One off Gift Aid donations"), false, LocalDate.of(2015, 3, 31), 880.00),
      GiftAidDonation(4, Some("Miss"), Some("B"), Some("Chaudry"), Some("21"), Some("L43 4FB"), None, true, LocalDate.of(2015, 4, 26), 80.00),
      GiftAidDonation(
        5,
        Some("Mr"),
        Some("John"),
        Some("Smith"),
        Some("100 Champs Elysees, Paris"),
        Some("X"),
        None,
        false,
        LocalDate.of(2015, 6, 24),
        250.00
      )
    )
  )

  val validRowsNoEarliestDonationDate = GiftAidScheduleData(
    None,
    None,
    Some(1450.00),
    List(
      GiftAidDonation(
        1,
        Some("Prof"),
        Some("Henry"),
        Some("House Martin"),
        Some("152A"),
        Some("M99 2QD"),
        None,
        false,
        LocalDate.of(2015, 3, 24),
        240.00
      ),
      GiftAidDonation(
        2,
        Some("Mr"),
        Some("John"),
        Some("Smith"),
        Some("100 Champs Elysees, Paris"),
        Some("X"),
        None,
        false,
        LocalDate.of(2015, 6, 24),
        250.00
      ),
      GiftAidDonation(3, None, None, None, None, None, Some("One off Gift Aid donations"), false, LocalDate.of(2015, 3, 31), BigDecimal(880.00)),
      GiftAidDonation(
        4,
        Some("Miss"),
        Some("B"),
        Some("Chaudry"),
        Some("21"),
        Some("L43 4FB"),
        None,
        true,
        LocalDate.of(2015, 4, 26),
        80.00
      )
    )
  )

  val BadEarliestDateOverClaimAmount: List[ValidationError] = List(
    ValidationError("earliestDonationDate", "validationService.giftAid.message.3"),
    ValidationError("previouslyOverClaimedAmount", "validationService.giftAid.message.4")
  )

  val BadDataValidationErrors: List[ValidationError] = List(
    ValidationError("earliestDonationDate", "validationService.giftAid.message.2"),
    ValidationError("previouslyOverClaimedAmount", "validationService.giftAid.message.4"),
    ValidationError("donorFirstName[0]", "validationService.giftAid.message.14"),
    ValidationError("donorLastName[1]", "validationService.giftAid.message.15"),
    ValidationError("donorHouse[2]", "validationService.giftAid.message.18"),
    ValidationError("postcode[3]", "validationService.giftAid.message.20"),
    ValidationError("donationDate[4]", "validationService.giftAid.message.7"),
    ValidationError("donationAmount[5]", "validationService.giftAid.message.11"),
    ValidationError("donationDate[6]", "validationService.giftAid.message.8"),
    ValidationError("donationDate[7]", "validationService.giftAid.message.9"),
    ValidationError("donationAmount[8]", "validationService.giftAid.message.10"),
    ValidationError(
      "donorTitle[9]",
      "validationService.giftAid.message.13"
    ),
    ValidationError(
      "donorFirstName[10]",
      "validationService.giftAid.message.16"
    ),
    ValidationError(
      "donorLastName[11]",
      "validationService.giftAid.message.17"
    ),
    ValidationError(
      "donorHouse[12]",
      "validationService.giftAid.message.19"
    ),
    ValidationError("sponsoredEvent[13]", "validationService.giftAid.message.22"),
    ValidationError(
      "aggregateDonationsConflict[14]",
      "validationService.giftAid.message.23"
    ),
    ValidationError("donationAmount[14]", "validationService.giftAid.message.12"),
    ValidationError(
      "aggregateDonationsConflict[15]",
      "validationService.giftAid.message.24"
    ),
    ValidationError("donationAmount[15]", "validationService.giftAid.message.12"),
    ValidationError(
      "aggregateDonationsConflict[16]",
      "validationService.giftAid.message.25"
    ),
    ValidationError("donationAmount[16]", "validationService.giftAid.message.12"),
    ValidationError(
      "aggregateDonationsConflict[17]",
      "validationService.giftAid.message.26"
    ),
    ValidationError("donationAmount[17]", "validationService.giftAid.message.12"),
    ValidationError(
      "aggregateDonationsConflict[18]",
      "validationService.giftAid.message.27"
    ),
    ValidationError("donationAmount[18]", "validationService.giftAid.message.12"),
    ValidationError(
      "aggregateDonationsConflict[19]",
      "validationService.giftAid.message.28"
    ),
    ValidationError("donationAmount[19]", "validationService.giftAid.message.12"),
    ValidationError(
      "aggregateDonations[20]",
      "validationService.giftAid.message.5"
    ),
    ValidationError("donationAmount[21]", "validationService.giftAid.message.12"),
    ValidationError("item[22]", "validationService.giftAid.message.6"),
    ValidationError("item[23]", "validationService.giftAid.message.6"),
    ValidationError("donorFirstName[24]", "validationService.giftAid.message.14"),
    ValidationError("donorLastName[24]", "validationService.giftAid.message.15"),
    ValidationError("donorHouse[24]", "validationService.giftAid.message.18"),
    ValidationError("postcode[24]", "validationService.giftAid.message.20"),
    ValidationError("donationDate[24]", "validationService.giftAid.message.7"),
    ValidationError("donationAmount[24]", "validationService.giftAid.message.11"),
    ValidationError("donorFirstName[25]", "validationService.giftAid.message.14"),
    ValidationError("donorLastName[25]", "validationService.giftAid.message.15"),
    ValidationError("donorHouse[25]", "validationService.giftAid.message.18"),
    ValidationError("postcode[25]", "validationService.giftAid.message.20")
  )
}
