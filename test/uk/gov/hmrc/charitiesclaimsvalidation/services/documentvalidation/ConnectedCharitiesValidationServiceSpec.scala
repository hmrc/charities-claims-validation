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

package uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation

import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{Charity, ConnectedCharitiesData}
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.ConnectedCharitiesRow
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.ValidationError
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.ConnectedCharitiesValidationService.ConnectedCharitiesRowWithIndex
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.ConnectedCharitiesValidationServiceSpec.*
import uk.gov.hmrc.charitiesclaimsvalidation.util.BaseSpec
import cats.effect.unsafe.implicits.global

class ConnectedCharitiesValidationServiceSpec extends BaseSpec {

  val expectedResultForMultipleSpacesAndNewLines = ConnectedCharitiesData(charities =
    List(
      Charity(charityItem = Some(1), charityName = Some("Charity of the 501st Legion"), charityReference = Some("CW501")),
      Charity(charityItem = Some(2), charityName = Some("Save the Children --- UK Trust"), charityReference = Some("CW789")),
      Charity(charityItem = Some(200), charityName = Some("Test Charity"), charityReference = Some("CW800"))
    )
  )

  "validate" - {
    "return a list of valid rows with no errors given a valid connected charities spreadsheet" in {
      val (errorResult, validResult) = new ConnectedCharitiesValidationService().validate(connectedCharitiesGoodDataPath).futureValue
      val expectedResult = ConnectedCharitiesData(charities =
        List(
          Charity(charityItem = Some(1), charityName = Some("Charity of the 501st Legion"), charityReference = Some("CW501")),
          Charity(charityItem = Some(200), charityName = Some("Test Charity"), charityReference = Some("CW800"))
        )
      )

      errorResult shouldBe empty
      validResult.value shouldBe expectedResult
    }

    "return a list of valid rows with no errors given a valid connected charities spreadsheet with different attributes" in {
      val (errorResult, validResult) = new ConnectedCharitiesValidationService().validate(connectedCharitiesGoodDataWithAttributesPath).futureValue
      val expectedResult = ConnectedCharitiesData(charities =
        List(
          Charity(charityItem = Some(1), charityName = Some("Save the Children --- UK Trust"), charityReference = Some("CW789")),
          Charity(charityItem = Some(4), charityName = Some("Test Charity"), charityReference = Some("CW123")),
          Charity(charityItem = Some(4), charityName = Some("Test Charity"), charityReference = Some("CW123")),
          Charity(charityItem = Some(4), charityName = Some("Test Charity"), charityReference = Some("CW123")),
          Charity(charityItem = Some(4), charityName = Some("Test Charity"), charityReference = Some("CW123")),
          Charity(charityItem = Some(200), charityName = Some("200th Charity"), charityReference = Some("CW777"))
        )
      )

      errorResult shouldBe empty
      validResult.value shouldBe expectedResult
    }

    "return a list of valid rows with multiple spaces in charityName, after normalising the spacing no errors given a valid connected charities spreadsheet" in {
      val (errorResult, validResult) =
        new ConnectedCharitiesValidationService().validate(connectedCharitiesGoodDataWithMultipleSpacesPath).futureValue

      errorResult shouldBe empty
      validResult.value shouldBe expectedResultForMultipleSpacesAndNewLines
    }

    "return a list of valid rows with new lines, after removing new lines no errors given a valid connected charities spreadsheet" in {
      val (errorResult, validResult) =
        new ConnectedCharitiesValidationService().validate(connectedCharitiesGoodDataWithMultipleSpacesPath).futureValue

      errorResult shouldBe empty
      validResult.value shouldBe expectedResultForMultipleSpacesAndNewLines
    }

    "return a list of invalid and valid rows given spreadsheet with invalid data" in {
      val (errorResult, validResult) = new ConnectedCharitiesValidationService().validate(connectedCharitiesBadDataPath).futureValue
      val expectedResult = ConnectedCharitiesData(charities =
        List(
          Charity(
            charityItem = Some(1),
            charityName = None,
            charityReference = Some("CW501")
          ),
          Charity(
            charityItem = Some(2),
            charityName = Some("Charity of the 501st Legion"),
            charityReference = None
          ),
          Charity(
            charityItem = Some(3),
            charityName = None,
            charityReference = Some("CW501"),
            enteredValues = Map("charityName" -> "a" * 509)
          ),
          Charity(
            charityItem = Some(4),
            charityName = Some("Charity of the 501st Legion"),
            charityReference = None,
            enteredValues = Map("charityReference" -> "*&^%$\u00A3\"!")
          ),
          Charity(
            charityItem = None,
            charityName = Some("Charity of the 501st Legion"),
            charityReference = Some("CW501"),
            enteredValues = Map("charityItem" -> "555555")
          ),
          Charity(
            charityItem = Some(6),
            charityName = Some("Test Charity"),
            charityReference = Some("AB134")
          )
        )
      )

      errorResult shouldBe BadDataValidationErrors
      validResult.value shouldBe expectedResult
    }

    "return no errors and no valid rows when spreadsheet is empty" in {
      val (errorResult, validResult) = new ConnectedCharitiesValidationService().validate(connectedCharitiesEmptyDataPath).futureValue

      errorResult shouldBe List(
        ValidationError(
          "connectedCharities",
          "validationService.connectedCharities.message.1"
        )
      )
      validResult should not be defined
    }

    "return a single validation error if the sheet name is incorrect" in {
      val (errorResult, validResult) = new ConnectedCharitiesValidationService().validate(connectedCharitiesBadSheetNameDataPath).futureValue

      errorResult shouldBe List(
        ValidationError(
          "fileError",
          "validationService.commonFile.message.3"
        )
      )
      validResult should not be defined
    }
  }

  "validateRows " - {

    "validate item values" - {

      val itemError = ValidationError(
        "item[0]",
        "validationService.connectedCharities.message.2"
      )

      "return no errors when item is between 1 and 200" in {
        List("1", "200", " 5 ").foreach { item =>
          val (errorRows, validRows) = ConnectedCharitiesValidationService.validateRows(
            List(ConnectedCharitiesRowWithIndex(0, ConnectedCharitiesRow(item, "Charity", "AB123")))
          )

          errorRows shouldBe empty
          validRows should have size 1
        }
      }

      "return a validation error when item is out of range, non-numeric, or padded with zeros" in {
        List("0", "201", "999", "1234", "01", "abc", "").foreach { item =>
          val (errorRows, validRows) = ConnectedCharitiesValidationService.validateRows(
            List(ConnectedCharitiesRowWithIndex(15, ConnectedCharitiesRow(item, "Valid", "AB123")))
          )

          errorRows should contain(itemError)
          validRows.map(_.charityItem) shouldBe List(None)
          validRows.map(_.enteredValues.get("charityItem")) shouldBe List(Option(item.trim).filter(_.nonEmpty))
        }
      }
    }

    "validate charity name" - {

      val charityNameMissingError = ValidationError(
        "charityName[0]",
        "validationService.connectedCharities.message.3"
      )
      val charityNameInvalidError =
        ValidationError(
          "charityName[0]",
          "validationService.connectedCharities.message.4"
        )

      "return no errors when charity name is valid" in {
        List("Valid Charity", "A", "a" * 160).foreach { name =>
          val (errorRows, validRows) = ConnectedCharitiesValidationService.validateRows(
            List(ConnectedCharitiesRowWithIndex(0, ConnectedCharitiesRow("1", name, "AB123")))
          )

          errorRows shouldBe empty
          validRows should have size 1
        }
      }

      "return a validation error when the name is missing" in {
        List("", "   ", "\t").foreach { name =>
          val (errorRows, validRows) = ConnectedCharitiesValidationService.validateRows(
            List(ConnectedCharitiesRowWithIndex(15, ConnectedCharitiesRow("1", name, "AB123")))
          )

          errorRows should contain(charityNameMissingError)
          validRows.map(_.charityName) shouldBe List(None)
          validRows.flatMap(_.enteredValues.get("charityName")) shouldBe empty
        }
      }

      "return a validation error when the name is not in valid format" in {
        List("a" * 161).foreach { name =>
          val (errorRows, validRows) = ConnectedCharitiesValidationService.validateRows(
            List(ConnectedCharitiesRowWithIndex(15, ConnectedCharitiesRow("1", name, "AB123")))
          )

          errorRows should contain(charityNameInvalidError)
          validRows.map(_.charityName) shouldBe List(None)
          validRows.map(_.enteredValues.get("charityName")) shouldBe List(Some(name))
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
          val (errorRows, validRows) = ConnectedCharitiesValidationService.validateRows(
            List(ConnectedCharitiesRowWithIndex(15, ConnectedCharitiesRow("1", name, "AB123")))
          )

          errorRows should contain(charityNameMissingError)
          validRows.map(_.charityName) shouldBe List(None)
          validRows.map(_.enteredValues.get("charityName")) shouldBe List(Some(name.trim))
        }
      }
    }

    "validate charity reference" - {

      val charityReferenceMissingError = ValidationError(
        "charityReference[0]",
        "validationService.connectedCharities.message.5"
      )
      val charityReferenceInvalidError =
        ValidationError(
          "charityReference[0]",
          "validationService.connectedCharities.message.6"
        )

      "return no errors when charity reference is valid" in {
        List("A1", "AB123", "Z99999", "C456").foreach { reference =>
          val (errorRows, validRows) = ConnectedCharitiesValidationService.validateRows(
            List(ConnectedCharitiesRowWithIndex(0, ConnectedCharitiesRow("1", "Test Charity", reference)))
          )

          errorRows shouldBe empty
          validRows should have size 1
        }
      }

      "return a validation error when the charity reference is missing" in {
        List("", "   ", "\t").foreach { reference =>
          val (errorRows, validRows) = ConnectedCharitiesValidationService.validateRows(
            List(ConnectedCharitiesRowWithIndex(15, ConnectedCharitiesRow("1", "Test Charity", reference)))
          )

          errorRows should contain(charityReferenceMissingError)
          validRows.map(_.charityReference) shouldBe List(None)
          validRows.flatMap(_.enteredValues.get("charityReference")) shouldBe empty
        }
      }

      "return a validation error when the charity reference is not in valid format" in {
        List("abc123", "AB", "123", "ABC123").foreach { reference =>
          val (errorRows, validRows) = ConnectedCharitiesValidationService.validateRows(
            List(ConnectedCharitiesRowWithIndex(15, ConnectedCharitiesRow("1", "Test Charity", reference)))
          )

          errorRows should contain(charityReferenceInvalidError)
          validRows.map(_.charityReference) shouldBe List(None)
          validRows.map(_.enteredValues.get("charityReference")) shouldBe List(Some(reference))
        }
      }
    }

    "return multiple validation errors when multiple fields are invalid" in {
      val (errorRows, validRows) = ConnectedCharitiesValidationService.validateRows(
        List(ConnectedCharitiesRowWithIndex(15, ConnectedCharitiesRow("1", "", "Invalid_ref")))
      )

      errorRows should have size 2
      errorRows should contain allOf (
        ValidationError(
          "charityName[0]",
          "validationService.connectedCharities.message.3"
        ),
        ValidationError(
          "charityReference[0]",
          "validationService.connectedCharities.message.6"
        )
      )
      validRows.map(_.charityName) shouldBe List(None)
      validRows.map(_.charityReference) shouldBe List(None)
      validRows.map(_.enteredValues) shouldBe List(Map("charityReference" -> "Invalid_ref"))
    }

    "ensure non western chars are removed from inputs like the AS IS system" in {
      val (errorRows, validRows) = ConnectedCharitiesValidationService.validateRows(
        List(ConnectedCharitiesRowWithIndex(0, ConnectedCharitiesRow("☺1", "☺Charity of the 501st Legion", "☺CW501")))
      )

      errorRows should have size 0
      validRows should have size 1
    }
  }
}

object ConnectedCharitiesValidationServiceSpec {
  val connectedCharitiesGoodDataPath: String =
    new java.io.File("test/resources/connectedCharities/connected_charities_schedule__Excel_GoodData.ods").toURI.toURL.toString
  val connectedCharitiesGoodDataWithAttributesPath: String =
    new java.io.File("test/resources/connectedCharities/connected_charities_schedule__Excel_GoodDataWithAttributes.ods").toURI.toURL.toString
  val connectedCharitiesGoodDataWithMultipleSpacesPath: String =
    new java.io.File("test/resources/connectedCharities/connected_charities_schedule__Excel_GoodDataWithMultipleSpaces.ods").toURI.toURL.toString
  val connectedCharitiesBadDataPath: String =
    new java.io.File("test/resources/connectedCharities/connected_charities_schedule__Excel_BadData.ods").toURI.toURL.toString
  val connectedCharitiesEmptyDataPath: String =
    new java.io.File("test/resources/connectedCharities/connected_charities_schedule__Excel_EmptyData.ods").toURI.toURL.toString
  val connectedCharitiesBadSheetNameDataPath: String =
    new java.io.File("test/resources/connectedCharities/connected_charities_schedule__Excel_BadSheetNameData.ods").toURI.toURL.toString

  // Row indices start at 15 (from ConnectedCharitiesRow.layout.rowRange = 15 until 215)
  val BadDataValidationErrors: List[ValidationError] = List(
    ValidationError("charityName[0]", "validationService.connectedCharities.message.3"),
    ValidationError("charityReference[1]", "validationService.connectedCharities.message.5"),
    ValidationError("charityName[2]", "validationService.connectedCharities.message.4"),
    ValidationError("charityReference[3]", "validationService.connectedCharities.message.6"),
    ValidationError("item[4]", "validationService.connectedCharities.message.2")
  )
}
