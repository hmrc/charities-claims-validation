/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation

import org.scalatest.prop.TableDrivenPropertyChecks
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.OtherIncomeValidationServiceSpec.*
import uk.gov.hmrc.charitiesclaimsvalidation.util.BaseSpec
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{OtherIncome, OtherIncomeData, ValidationError}
import cats.effect.unsafe.implicits.global
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.OtherIncomeRow
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.OtherIncomeValidationService.OtherRowWithIndex
import java.time.LocalDate

/*
validationService.otherIncome.message.1 = Enter details for an Other Income item
validationService.otherIncome.message.2 = Enter an overclaimed amount in the correct format
validationService.otherIncome.message.3 = Tax deducted must be less than gross payment
validationService.otherIncome.message.4 = There is an issue with this item number
validationService.otherIncome.message.5 = Enter a name of payer
validationService.otherIncome.message.6 = Enter a name of payer in the correct format
validationService.otherIncome.message.7 = Enter an income date
validationService.otherIncome.message.8 = Enter income date in the correct format
validationService.otherIncome.message.9 = Other income date of payment cannot be in the future
validationService.otherIncome.message.10 = Enter a gross payment amount
validationService.otherIncome.message.11 = Enter the tax deduction amount
validationService.otherIncome.message.12 = Enter a gross payment amount in the correct format
validationService.otherIncome.message.13 = Enter the tax deduction amount in the correct format
 */

class OtherIncomeValidationServiceSpec extends BaseSpec with TableDrivenPropertyChecks {

  val expectedResultForMultipleSpacesAndNewLines = Some(
    OtherIncomeData(
      adjustmentForOtherIncomePreviousOverClaimed = Some(78.00),
      totalOfGrossPayments = Some(9257.00),
      totalOfTaxDeducted = Some(192.00),
      otherIncomes = List(
        OtherIncome(
          otherIncomeItem = 1,
          payerName = "Test User",
          paymentDate = LocalDate.of(2025, 1, 1),
          grossPayment = 1234.00,
          taxDeducted = 56.00
        ),
        OtherIncome(
          otherIncomeItem = 2,
          payerName = "Test 2nd User",
          paymentDate = LocalDate.of(2025, 2, 28),
          grossPayment = 6789.00,
          taxDeducted = 80.00
        ),
        OtherIncome(
          otherIncomeItem = 3,
          payerName = "Test UserOther",
          paymentDate = LocalDate.of(2025, 1, 1),
          grossPayment = 1234.00,
          taxDeducted = 56.00
        )
      )
    )
  )

  "validate" - {
    "return a list of valid rows with no errors given a valid other income spreadsheet" in {
      val (errorResult, validResult) = new OtherIncomeValidationService().validate(OtherIncomeGoodDataPath).futureValue
      val expectedResult = Some(
        OtherIncomeData(
          adjustmentForOtherIncomePreviousOverClaimed = Some(78.00),
          totalOfGrossPayments = Some(8023.00),
          totalOfTaxDeducted = Some(136.00),
          otherIncomes = List(
            OtherIncome(
              otherIncomeItem = 1,
              payerName = "Test User",
              paymentDate = LocalDate.of(2025, 1, 1),
              grossPayment = 1234.00,
              taxDeducted = 56.00
            ),
            OtherIncome(
              otherIncomeItem = 2,
              payerName = "Test 2nd User",
              paymentDate = LocalDate.of(2025, 2, 28),
              grossPayment = 6789.00,
              taxDeducted = 80.00
            )
          )
        )
      )

      errorResult shouldBe empty
      validResult shouldBe expectedResult
    }

    "return a list of valid rows with multiple spaces in payerName, after normalising the spacing no errors given a valid other income spreadsheet" in {
      val (errorResult, validResult) = new OtherIncomeValidationService().validate(OtherIncomeGoodDataWithMultipleSpacesPath).futureValue

      errorResult shouldBe empty
      validResult shouldBe expectedResultForMultipleSpacesAndNewLines
    }

    "return a list of valid rows with new lines, after removing new lines no errors given a valid other income spreadsheet" in {
      val (errorResult, validResult) = new OtherIncomeValidationService().validate(OtherIncomeGoodDataWithMultipleSpacesPath).futureValue

      errorResult shouldBe empty
      validResult shouldBe expectedResultForMultipleSpacesAndNewLines
    }

    "return a list of invalid and valid rows given spreadsheet with invalid data" in {
      val (errorResult, validResult) = new OtherIncomeValidationService().validate(OtherIncomeBadDataPath).futureValue
      val expectedResult = Some(
        OtherIncomeData(None, Some(1234.00), Some(56.00), List(OtherIncome(11, "missing item number", LocalDate.of(2025, 1, 1), 1234.00, 56.00)))
      )

      errorResult shouldBe BadDataValidationErrors
      validResult shouldBe expectedResult
    }

    "return a list of invalid and valid rows given spreadsheet with invalid data and adjusted income is empty" in {
      val (errorResult, validResult) = new OtherIncomeValidationService().validate(OtherIncomeBadNoAdjIncDataPath).futureValue
      val expectedResult = Some(
        OtherIncomeData(None, Some(1234.00), Some(56.00), List(OtherIncome(11, "missing item number", LocalDate.of(2025, 1, 1), 1234.00, 56.00)))
      )

      errorResult shouldBe BadDataValidationErrorsNoAdjInc
      validResult shouldBe expectedResult
    }

    "return a single validation error if the rows contain no data" in {
      val (errorResult, validResult) = new OtherIncomeValidationService().validate(OtherIncomeEmptyDataDataPath).futureValue

      errorResult shouldBe List(
        ValidationError(
          "otherIncomes",
          "validationService.otherIncome.message.1"
        )
      )
      validResult shouldBe None
    }

    "return a single validation error if the sheet name is incorrect" in {
      val (errorResult, validResult) = new OtherIncomeValidationService().validate(OtherIncomeBadSheetNameDataPath).futureValue

      errorResult shouldBe List(
        ValidationError(
          "fileError",
          "validationService.commonFile.message.3"
        )
      )
      validResult shouldBe None
    }

    "check invalid characters are removed from the input strings" in {
      val (errorRows, validResult) = OtherIncomeValidationService.validateRows(
        List(
          OtherRowWithIndex(
            0,
            OtherIncomeRow(
              "☺1",
              "☺Test User",
              "01/0☺1/25",
              "☺1234.00",
              "☺56.00"
            )
          )
        ),
        LocalDate.now()
      )

      errorRows should have size 0
      validResult should have size 1
    }

    "return a validation error when payer name contains only blank-looking characters" in {
      val blankInputs = List(
        "–",            // en dash
        "—",            // em dash
        "…",            // ellipsis
        "\u00A0",       // non-breaking space
        "– –",          // en dash + space + en dash
        "\u2018 \u2019" // smart quotes around a space
      )
      blankInputs.foreach { name =>
        val (errorRows, validRows) = OtherIncomeValidationService.validateRows(
          List(OtherRowWithIndex(24, OtherIncomeRow("1", name, "01/01/25", "100.00", "10.00"))),
          LocalDate.now()
        )

        errorRows should contain(ValidationError("payerName[0]", "validationService.otherIncome.message.5"))
        validRows shouldBe empty
      }
    }
  }

  "return a list of invalid rows given spreadsheet where tax deducted must be less than the gross payment" in {
    val (errorResult, validResult) = new OtherIncomeValidationService().validate(OtherIncomeBadCrossFieldDataPath).futureValue

    errorResult shouldBe List(
      ValidationError("taxDeducted[0]", "validationService.otherIncome.message.13"),
      ValidationError("taxDeducted[0]", "validationService.otherIncome.message.13"),
      ValidationError("taxDeducted[0]", "validationService.otherIncome.message.3")
    )
  }

}

object OtherIncomeValidationServiceSpec {
  val OtherIncomeGoodDataPath: String = new java.io.File("test/resources/otherincome/other_income_schedule-GoodData.ods").toURI.toURL.toString
  val OtherIncomeGoodDataWithMultipleSpacesPath: String =
    new java.io.File("test/resources/otherincome/other_income_schedule-GoodDataWithMultipleSpaces.ods").toURI.toURL.toString
  val OtherIncomeBadDataPath: String = new java.io.File("test/resources/otherincome/other_income_schedule-BadData.ods").toURI.toURL.toString
  val OtherIncomeBadNoAdjIncDataPath: String =
    new java.io.File("test/resources/otherincome/other_income_schedule-BadData-NoAdjustedIncome.ods").toURI.toURL.toString
  val OtherIncomeEmptyDataDataPath: String =
    new java.io.File("test/resources/otherincome/other_income_schedule-EmptyData.ods").toURI.toURL.toString
  val OtherIncomeBadSheetNameDataPath: String =
    new java.io.File("test/resources/otherincome/other_income_schedule-BadSheetName.ods").toURI.toURL.toString
  val OtherIncomeBadCrossFieldDataPath: String =
    new java.io.File("test/resources/otherincome/other_income_schedule-BadDataCrossField.ods").toURI.toURL.toString

  // Row indices start at 24 (from OtherIncomeRow.layout.rowRange = 24 until 224)
  val BadDataValidationErrors: List[ValidationError] = List(
    ValidationError(
      "adjustmentForOtherIncomePreviouslyOverClaimed",
      "validationService.otherIncome.message.2"
    ),
    ValidationError("payerName[0]", "validationService.otherIncome.message.5"),
    ValidationError("paymentDate[1]", "validationService.otherIncome.message.7"),
    ValidationError("grossPayment[2]", "validationService.otherIncome.message.10"),
    ValidationError("taxDeducted[3]", "validationService.otherIncome.message.11"),
    ValidationError("payerName[4]", "validationService.otherIncome.message.6"),
    ValidationError("paymentDate[5]", "validationService.otherIncome.message.8"),
    ValidationError("paymentDate[6]", "validationService.otherIncome.message.9"),
    ValidationError("grossPayment[7]", "validationService.otherIncome.message.12"),
    ValidationError("taxDeducted[8]", "validationService.otherIncome.message.13"),
    ValidationError("item[9]", "validationService.otherIncome.message.4"),
    ValidationError("taxDeducted[9]", "validationService.otherIncome.message.3"),
    ValidationError("item[11]", "validationService.otherIncome.message.4"),
    ValidationError("paymentDate[12]", "validationService.otherIncome.message.8")
  )

  // Row indices start at 24 (from OtherIncomeRow.layout.rowRange = 24 until 224)
  val BadDataValidationErrorsNoAdjInc: List[ValidationError] = List(
    ValidationError("payerName[0]", "validationService.otherIncome.message.5"),
    ValidationError("paymentDate[1]", "validationService.otherIncome.message.7"),
    ValidationError("grossPayment[2]", "validationService.otherIncome.message.10"),
    ValidationError("taxDeducted[3]", "validationService.otherIncome.message.11"),
    ValidationError("payerName[4]", "validationService.otherIncome.message.6"),
    ValidationError("paymentDate[5]", "validationService.otherIncome.message.8"),
    ValidationError("paymentDate[6]", "validationService.otherIncome.message.9"),
    ValidationError("grossPayment[7]", "validationService.otherIncome.message.12"),
    ValidationError("taxDeducted[8]", "validationService.otherIncome.message.13"),
    ValidationError("item[9]", "validationService.otherIncome.message.4"),
    ValidationError("taxDeducted[9]", "validationService.otherIncome.message.3")
  )
}
