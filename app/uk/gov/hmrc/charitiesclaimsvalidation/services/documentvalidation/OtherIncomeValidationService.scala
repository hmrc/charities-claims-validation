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
import uk.gov.hmrc.charitiesclaimsvalidation.models.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.errors.{BadSheetNameException, NoRowsFoundException, NotAnOdsFileException}
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{OtherIncome, OtherIncomeData, ValidationError, ValidationType}
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.OtherIncomeRow
import CommonFileValidation.{discarded, removeNonWesternCharacters, sheetNameIsDifferent, spreadsheetFileNotFound, spreadsheetUnexpectedError, verifySheetName}

import java.io.FileNotFoundException
import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import play.api.Logging
import scala.concurrent.Future
import scala.util.Try

@Singleton()
class OtherIncomeValidationService @Inject() ()(using ioRuntime: IORuntime) extends Logging {

  def validate(path: String): Future[(List[ValidationError], Option[OtherIncomeData])] =
    validateRowsIO(path).unsafeToFuture()

  private def validateRowsIO(downloadUrl: String): IO[(List[ValidationError], Option[OtherIncomeData])] =
    OdsReaderService
      .withDocumentStream(downloadUrl) { doc =>
        for {
          sheetName                   <- verifySheetName(doc, ValidationType.OtherIncome)
          rawOtherIncomeRowsWithIndex <- OdsReaderService.rowsFromDocumentWithIndex[OtherIncomeRow](doc, OtherIncomeRow.layout)
          _                           <- IO.raiseWhen(rawOtherIncomeRowsWithIndex.isEmpty)(NoRowsFoundException)

          adjustmentForOtherIncomeCell <- OdsReaderService.cellFromDocument(doc, OtherIncomeRow.ADJ_OTHER_INCOME_PREV_CLAIM_FIELD)
          otherIncomeRowWithIndex = rawOtherIncomeRowsWithIndex.map { case (idx, row) => OtherIncomeValidationService.OtherRowWithIndex(idx, row) }
          (rowErrors, otherIncomeRows) = OtherIncomeValidationService.validateRows(otherIncomeRowWithIndex, today = LocalDate.now)
          validatedAdjustment          = OtherIncomeValidationService.validateAdjustment(adjustmentForOtherIncomeCell)
          totalGrossPayments           = Some(otherIncomeRows.flatMap(_.grossPayment).fold(BigDecimal(0))(_ + _))
          totalTaxDeducted             = Some(otherIncomeRows.flatMap(_.taxDeducted).fold(BigDecimal(0))(_ + _))
        } yield validatedAdjustment match {
          case Left(validationError) =>
            (validationError :: rowErrors, OtherIncomeData(None, totalGrossPayments, totalTaxDeducted, otherIncomeRows).some)
          case Right(Some(adjAmount)) => (rowErrors, OtherIncomeData(adjAmount.some, totalGrossPayments, totalTaxDeducted, otherIncomeRows).some)
          case Right(None)            => (rowErrors, OtherIncomeData(None, totalGrossPayments, totalTaxDeducted, otherIncomeRows).some)
        }
      }
      .recover {
        case NoRowsFoundException =>
          logger.warn("OtherIncome validation failed: spreadsheet contains no data")
          (List(OtherIncomeValidationService.spreadsheetContainsNoData), None)
        case _: FileNotFoundException =>
          logger.warn("OtherIncome validation failed: spreadsheet file not found")
          (List(spreadsheetFileNotFound), None)
        case _: BadSheetNameException =>
          logger.warn("OtherIncome validation failed: incorrect sheet name")
          (List(sheetNameIsDifferent), None)
        case _: NotAnOdsFileException =>
          logger.warn("OtherIncome validation failed: not an ODF spreadsheet")
          (List(sheetNameIsDifferent), None)
        case ex =>
          logger.error(s"OtherIncome validation failed with unexpected error: ${ex.getMessage}", ex)
          (List(spreadsheetUnexpectedError), None)
      }

}

object OtherIncomeValidationService {

  type V[A] = ValidatedNel[ValidationError, A]

  final case class OtherRowWithIndex(index: Int, row: OtherIncomeRow)

  private val moneyMin = BigDecimal("0.01")
  private val moneyMax = BigDecimal("9999999999999.99")

  private val spreadsheetContainsNoData =
    ValidationError(
      "otherIncomes",
      "validationService.otherIncome.message.1"
    )

  def validateRows(
    inputRows: List[OtherRowWithIndex],
    today: LocalDate
  ): (List[ValidationError], List[OtherIncome]) = {

    val validated = inputRows.map(validateRow(_, today))

    (validated.flatMap(_._1), validated.map(_._2))
  }

  private def validateAdjustment(adjustmentOtherIncomeCell: String): Either[ValidationError, Option[BigDecimal]] = {
    val fieldKey              = "adjustmentForOtherIncomePreviouslyOverClaimed"
    val errorMessage          = s"validationService.otherIncome.message.2"
    val removeNonWesternChars = removeNonWesternCharacters(adjustmentOtherIncomeCell)
    val normalized            = removeNonWesternChars.trim.replace(",", "")

    if (normalized.isEmpty) Right(None)
    else {
      Try(BigDecimal(normalized)).toOption match {
        case Some(amount) =>
          val scaleOk  = amount.scale == 2
          val digitCnt = amount.bigDecimal.unscaledValue.abs.toString.length
          val digitsOk = digitCnt <= 15
          if !scaleOk then Left(ValidationError(fieldKey, errorMessage))
          else if !digitsOk then Left(ValidationError(fieldKey, errorMessage))
          else Right(Some(amount))
        case None => Left(ValidationError(fieldKey, errorMessage))
      }
    }
  }

  private def validateRow(otherRowWithIndex: OtherRowWithIndex, today: LocalDate): (List[ValidationError], OtherIncome) = {
    import otherRowWithIndex.*

    val errorIndex = index - OtherIncomeRow.layout.rowRange.start

    val itemV = validateItem(row.otherIncomeItem, errorIndex)

    val nameV  = validateName(row.payerName, errorIndex)
    val dateV  = validateDate(row.paymentDate, errorIndex, today)
    val grossV = validateMoney(row.grossPayment, "a Gross payment", s"grossPayment[$errorIndex]")
    val taxV   = validateMoney(row.taxDeducted, "the tax deduction", s"taxDeducted[$errorIndex]")

    val grossPaymentTry = Validated.fromTry(Try(BigDecimal(row.grossPayment.trim.replace(",", ""))))
    val taxDeductedTry  = Validated.fromTry(Try(BigDecimal(row.taxDeducted.trim.replace(",", ""))))

    val taxLessThanGrossV: V[Unit] =
      (grossPaymentTry, taxDeductedTry) match {
        case (Validated.Valid(gross), Validated.Valid(tax)) =>
          if (tax < gross) ().validNel
          else
            ValidationError(
              field = s"taxDeducted[$errorIndex]",
              error = s"validationService.otherIncome.message.3"
            ).invalidNel

        case _ =>
          ().validNel
      }

    val errors = List[V[Any]](
      itemV,
      nameV,
      dateV,
      grossV,
      taxV,
      taxLessThanGrossV
    ).collect { case Validated.Invalid(errs) => errs.toList }.flatten

    val enteredValues = List(
      "otherIncomeItem" -> discarded(itemV.toOption, row.otherIncomeItem),
      "payerName"       -> discarded(nameV.toOption, row.payerName),
      "paymentDate"     -> discarded(dateV.toOption, row.paymentDate),
      "grossPayment"    -> discarded(grossV.toOption, row.grossPayment),
      "taxDeducted"     -> discarded(taxV.toOption, row.taxDeducted)
    ).collect { case (field, Some(value)) => field -> value }.toMap

    val otherIncome = OtherIncome(
      itemV.toOption,
      nameV.toOption,
      dateV.toOption,
      grossV.toOption,
      taxV.toOption,
      enteredValues
    )

    (errors, otherIncome)
  }

  private def validateItem(raw: String, index: Int): V[Int] = {
    val field = s"item[$index]"
    val t     = removeNonWesternCharacters(raw)

    if !t.matches("^[1-9][0-9]{0,2}$") then
      invalid(
        field,
        "validationService.otherIncome.message.4"
      )
    else {
      val value = t.toInt
      if value < 1 || value > 200 then
        invalid(
          field,
          "validationService.otherIncome.message.4"
        )
      else value.validNel
    }
  }

  private def validateName(raw: String, index: Int): V[String] = {
    val field = s"payerName[$index]"
    val t     = removeNonWesternCharacters(raw, true)

    if t.isEmpty then
      invalid(
        field,
        "validationService.otherIncome.message.5"
      )
    else if t.length > 40 then
      invalid(
        field,
        "validationService.otherIncome.message.6"
      )
    else t.validNel
  }

  private def validateDate(
    raw: String,
    index: Int,
    today: LocalDate
  ): V[LocalDate] = {
    val field = s"paymentDate[$index]"
    val t     = removeNonWesternCharacters(raw)

    if t.isEmpty then
      invalid(
        field,
        s"validationService.otherIncome.message.7"
      )
    else {
      CommonFileValidation.parseDate(t) match {
        case None =>
          invalid(
            field,
            s"validationService.otherIncome.message.8"
          )
        case Some(d) if d.isAfter(today) =>
          invalid(
            field,
            s"validationService.otherIncome.message.9"
          )
        case Some(d) =>
          d.validNel
      }
    }
  }

  private def validateMoney(
    raw: String,
    fieldLabel: String,
    fieldKey: String
  ): V[BigDecimal] = {

    val t = removeNonWesternCharacters(raw)

    if t.isEmpty then
      invalid(
        fieldKey,
        if fieldLabel.toLowerCase.contains("a gross payment") then s"validationService.otherIncome.message.10"
        else s"validationService.otherIncome.message.11"
      )
    else {
      val normalized = t.replace(",", "")
      Try(BigDecimal(normalized)).toOption match {
        case None =>
          invalid(
            fieldKey,
            if fieldLabel.toLowerCase.contains("a gross payment") then s"validationService.otherIncome.message.12"
            else s"validationService.otherIncome.message.13"
          )

        case Some(amount) =>
          val scaleOk  = amount.scale == 2
          val digitCnt = amount.bigDecimal.unscaledValue.abs.toString.length
          val digitsOk = digitCnt <= 15
          val inRange  = amount >= moneyMin && amount <= moneyMax

          val errs: List[ValidationError] =
            List(
              Option.when(!scaleOk)(
                ValidationError(
                  fieldKey,
                  if fieldLabel.toLowerCase.contains("a gross payment") then s"validationService.otherIncome.message.12"
                  else s"validationService.otherIncome.message.13"
                )
              ),
              Option.when(!digitsOk)(
                ValidationError(
                  fieldKey,
                  if fieldLabel.toLowerCase.contains("a gross payment") then s"validationService.otherIncome.message.12"
                  else s"validationService.otherIncome.message.13"
                )
              ),
              Option.when(!inRange)(
                ValidationError(
                  fieldKey,
                  if fieldLabel.toLowerCase.contains("a gross payment") then s"validationService.otherIncome.message.12"
                  else s"validationService.otherIncome.message.13"
                )
              )
            ).flatten

          errs.toNel match {
            case Some(nel) => nel.invalid[BigDecimal]
            case None      => amount.validNel
          }
      }
    }
  }

  private def invalid(field: String, msg: String): V[Nothing] =
    ValidationError(field, msg).invalidNel

}
