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

import cats.data.{Validated, ValidatedNel}
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.ConnectedCharitiesRow
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.errors.{BadSheetNameException, NoRowsFoundException, NotAnOdsFileException}
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.CommonFileValidation.{discarded, enteredValuesOf, errorsOf, removeNonWesternCharacters, sheetNameIsDifferent, spreadsheetFileNotFound, spreadsheetUnexpectedError, verifySheetName}

import java.io.FileNotFoundException
import javax.inject.{Inject, Singleton}
import play.api.Logging
import scala.concurrent.Future

@Singleton()
class ConnectedCharitiesValidationService @Inject() ()(using ioRuntime: IORuntime) extends Logging {

  def validate(path: String): Future[(List[ValidationError], Option[ConnectedCharitiesData])] =
    validateRowsIO(path).unsafeToFuture()

  private def validateRowsIO(downloadUrl: String): IO[(List[ValidationError], Option[ConnectedCharitiesData])] =
    OdsReaderService
      .withDocumentStream(downloadUrl) { doc =>
        for {
          sheetName                     <- verifySheetName(doc, ValidationType.ConnectedCharities)
          connectCharitiesRowsWithIndex <- OdsReaderService.rowsFromDocumentWithIndex[ConnectedCharitiesRow](doc, ConnectedCharitiesRow.layout)
          _                             <- IO.raiseWhen(connectCharitiesRowsWithIndex.isEmpty)(NoRowsFoundException)

          (rowErrors, validatedRows) = processRows(connectCharitiesRowsWithIndex)
        } yield (rowErrors, Some(validatedRows))
      }
      .recover {
        case NoRowsFoundException =>
          logger.warn("ConnectedCharities validation failed: spreadsheet contains no data")
          (List(ConnectedCharitiesValidationService.spreadsheetContainsNoData), None)
        case _: FileNotFoundException =>
          logger.warn("ConnectedCharities validation failed: spreadsheet file not found")
          (List(spreadsheetFileNotFound), None)
        case _: BadSheetNameException =>
          logger.warn("ConnectedCharities validation failed: incorrect sheet name")
          (List(sheetNameIsDifferent), None)
        case _: NotAnOdsFileException =>
          logger.warn("ConnectedCharities validation failed: not an ODF spreadsheet")
          (List(sheetNameIsDifferent), None)
        case ex =>
          logger.error(s"ConnectedCharities validation failed with unexpected error: ${ex.getMessage}", ex)
          (List(spreadsheetUnexpectedError), None)
      }

  private def processRows(rowsWithIndex: List[(Int, ConnectedCharitiesRow)]): (List[ValidationError], ConnectedCharitiesData) = {
    val indexedRows     = rowsWithIndex.map { case (idx, row) => ConnectedCharitiesValidationService.ConnectedCharitiesRowWithIndex(idx, row) }
    val (errors, valid) = ConnectedCharitiesValidationService.validateRows(indexedRows)
    (errors, ConnectedCharitiesData(valid))
  }
}

object ConnectedCharitiesValidationService {

  type V[A] = ValidatedNel[ValidationError, A]

  final case class ConnectedCharitiesRowWithIndex(index: Int, row: ConnectedCharitiesRow)

  private val spreadsheetContainsNoData =
    ValidationError(
      "connectedCharities",
      "validationService.connectedCharities.message.1"
    )

  def validateRows(
    inputRows: List[ConnectedCharitiesRowWithIndex]
  ): (List[ValidationError], List[Charity]) = {

    val (rowErrors, charities) = inputRows.map(validateRow).unzip

    (rowErrors.flatten, charities)
  }

  private def validateRow(connectedCharitiesRowWithIndex: ConnectedCharitiesRowWithIndex): (List[ValidationError], Charity) = {
    import connectedCharitiesRowWithIndex.*

    val errorIndex = index - ConnectedCharitiesRow.layout.rowRange.start

    val itemV = validateItem(row.connectedCharitiesItem, errorIndex)

    val nameV      = validateName(row.charityName, errorIndex)
    val referenceV = validateReference(row.charityReference, errorIndex)

    val errors = errorsOf(itemV, nameV, referenceV)

    val enteredValues = enteredValuesOf(
      "charityItem"      -> discarded(itemV.toOption, row.connectedCharitiesItem),
      "charityName"      -> discarded(nameV.toOption, row.charityName),
      "charityReference" -> discarded(referenceV.toOption, row.charityReference)
    )

    val charity = Charity(
      charityItem = itemV.toOption,
      charityName = nameV.toOption,
      charityReference = referenceV.toOption,
      enteredValues = enteredValues
    )

    (errors, charity)
  }

  private def validateItem(raw: String, index: Int): V[Int] = {
    val field = s"item[$index]"
    val t     = removeNonWesternCharacters(raw)

    if !t.matches("^[1-9][0-9]{0,2}$") then
      invalid(
        field,
        s"validationService.connectedCharities.message.2"
      )
    else {
      val value = t.toInt
      if value < 1 || value > 200 then
        invalid(
          field,
          s"validationService.connectedCharities.message.2"
        )
      else value.validNel
    }
  }

  private def validateName(raw: String, index: Int): V[String] = {
    val field = s"charityName[$index]"
    val t     = removeNonWesternCharacters(raw, true)

    if t.isEmpty then
      invalid(
        field,
        s"validationService.connectedCharities.message.3"
      )
    else if t.length > 160 then
      invalid(
        field,
        s"validationService.connectedCharities.message.4"
      )
    else t.validNel
  }

  private def validateReference(raw: String, index: Int): V[String] = {
    val field   = s"charityReference[$index]"
    val t       = removeNonWesternCharacters(raw)
    val pattern = "^[A-Z]{1,2}[0-9]{1,5}$".r

    if t.isEmpty then
      invalid(
        field,
        "validationService.connectedCharities.message.5"
      )
    else if !pattern.matches(t) then
      invalid(
        field,
        "validationService.connectedCharities.message.6"
      )
    else t.validNel
  }

  private def invalid(field: String, msg: String): V[Nothing] =
    ValidationError(field, msg).invalidNel

}
