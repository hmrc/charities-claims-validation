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
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.errors.{BadSheetNameException, NoRowsFoundException}
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.GiftAidDonationRow
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.CommonFileValidation.{removeNonWesternCharacters, sheetNameIsDifferent, spreadsheetFileNotFound, spreadsheetUnexpectedError, verifySheetName}

import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.format.{DateTimeFormatter, ResolverStyle}
import javax.inject.{Inject, Singleton}
import play.api.Logging
import scala.concurrent.Future
import scala.util.Try

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

@Singleton()
class GiftAidValidationService @Inject() ()(using ioRuntime: IORuntime) extends Logging {

  def validate(path: String): Future[(List[ValidationError], Option[GiftAidScheduleData])] =
    validateRowsIO(path).unsafeToFuture()

  private def validateRowsIO(downloadUrl: String): IO[(List[ValidationError], Option[GiftAidScheduleData])] =
    OdsReaderService
      .withDocumentStream(downloadUrl) { doc =>
        for {
          sheetName           <- verifySheetName(doc, ValidationType.GiftAid)
          giftAidDonationRows <- OdsReaderService.rowsFromDocument[GiftAidDonationRow](doc, GiftAidDonationRow.layout)
          _                   <- IO.raiseWhen(giftAidDonationRows.isEmpty)(NoRowsFoundException)

          earliestDonationDateCell        <- OdsReaderService.cellFromDocument(doc, GiftAidDonationRow.EARLIEST_DONATION_DATE_FIELD)
          previouslyOverclaimedAmountCell <- OdsReaderService.cellFromDocument(doc, GiftAidDonationRow.PREVIOUSLY_OVERCLAIMED_AMOUNT_FIELD)

          result <- IO.pure(validateDocument(giftAidDonationRows, earliestDonationDateCell, previouslyOverclaimedAmountCell))
        } yield result
      }
      .recover {
        case NoRowsFoundException =>
          logger.warn("GiftAid validation failed: spreadsheet contains no data")
          (List(GiftAidValidationService.spreadsheetContainsNoData), None)
        case _: FileNotFoundException =>
          logger.warn("GiftAid validation failed: spreadsheet file not found")
          (List(spreadsheetFileNotFound), None)
        case _: BadSheetNameException =>
          logger.warn("GiftAid validation failed: incorrect sheet name")
          (List(sheetNameIsDifferent), None)
        case ex =>
          logger.error(s"GiftAid validation failed with unexpected error: ${ex.getMessage}", ex)
          (List(spreadsheetUnexpectedError), None)
      }

  private def validateDocument(
    rows: List[GiftAidDonationRow],
    earliestCell: String,
    overclaimedCell: String
  ): (List[ValidationError], Option[GiftAidScheduleData]) = {

    val (rowErrors, validatedRows) = processRows(rows)

    val totalDonations = Option.when(validatedRows.nonEmpty)(validatedRows.map(_.donationAmount).sum)

    val earliestResult    = GiftAidValidationService.validateEarliestDonationDate(earliestCell)
    val overclaimedResult = GiftAidValidationService.validatePreviouslyOverclaimedAmount(overclaimedCell)

    val allErrors      = earliestResult.left.toOption.toList ++ overclaimedResult.left.toOption.toList ++ rowErrors
    val hasGiftAidData = validatedRows.nonEmpty || earliestResult.isRight || overclaimedResult.isRight

    val giftAidScheduleData =
      Option.when(hasGiftAidData) {
        GiftAidScheduleData(
          earliestResult.toOption.flatten,
          overclaimedResult.toOption.flatten,
          totalDonations,
          validatedRows
        )
      }

    (allErrors, giftAidScheduleData)
  }

  private def processRows(rows: List[GiftAidDonationRow]): (List[ValidationError], List[GiftAidDonation]) = {
    val indexedRows = rows.zipWithIndex.map { case (row, idx) => GiftAidValidationService.GiftAidDonationRowWithIndex(idx, row) }
    GiftAidValidationService.validateRows(indexedRows)
  }
}

object GiftAidValidationService {

  type V[A] = ValidatedNel[ValidationError, A]
  final case class GiftAidDonationRowWithIndex(index: Int, row: GiftAidDonationRow)

  private val spreadsheetContainsNoData =
    ValidationError(
      "giftAidDonation",
      "validationService.giftAid.message.1"
    )

  private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/uu").withResolverStyle(ResolverStyle.STRICT)

  def validateRows(
    inputRows: List[GiftAidDonationRowWithIndex]
  ): (List[ValidationError], List[GiftAidDonation]) = {

    val validated: List[V[GiftAidDonation]] = inputRows.map(validateRow)

    val errors    = validated.collect { case Validated.Invalid(errs) => errs.toList }.flatten
    val validRows = validated.collect { case Validated.Valid(row) => row }

    (errors, validRows)
  }

  private def validateRow(giftAidDonationRowWithIndex: GiftAidDonationRowWithIndex): V[GiftAidDonation] = {
    import giftAidDonationRowWithIndex.{index, row}

    val itemV                   = validateItem(row.donationItem, index)
    val isAggregated            = DonationType.fromRow(row).isAggregated
    val aggregateDonationRulesV = validateAggregateDonationRules(row, index)

    (
      itemV,
      aggregateDonationRulesV,
      validateTitle(row.donorTitle, index, isAggregated),
      validateName(row.donorFirstName, "First name", s"donorFirstName[$index]", isAggregated),
      validateName(row.donorLastName, "Last name", s"donorLastName[$index]", isAggregated),
      validateHouseName(row.donorHouse, index, isAggregated),
      validatePostcode(row.donorPostcode, index, isAggregated),
      validateAggregateDonations(row.aggregatedDonations, index),
      validateSponsoredEvent(row.sponsoredEvent, index, isAggregated),
      validateDate(row.donationDate, index, LocalDate.now),
      validateMoney(row.donationAmount, index, isAggregated)
    ).mapN { (item, _, title, firstName, lastName, house, postCode, aggDonations, sponsored, date, amount) =>
      GiftAidDonation(item, title, firstName, lastName, house, postCode, aggDonations, sponsored, date, amount)
    }
  }

  private def validateEarliestDonationDate(earliestDonationDateCell: String): Either[ValidationError, Option[LocalDate]] = {
    val fieldKey       = "earliestDonationDate"
    val missingMessage = s"validationService.giftAid.message.2"
    val input          = removeNonWesternCharacters(earliestDonationDateCell)

    if input.isEmpty then Left(ValidationError(fieldKey, missingMessage))
    else {
      Try(LocalDate.parse(input, dateFormatter)).toOption match {
        case None =>
          Left(
            ValidationError(
              fieldKey,
              "validationService.giftAid.message.3"
            )
          )
        case Some(d) =>
          Right(Some(d))
      }
    }
  }

  private def validatePreviouslyOverclaimedAmount(previouslyOverClaimedAmountCell: String): Either[ValidationError, Option[BigDecimal]] = {
    val fieldKey              = "previouslyOverClaimedAmount"
    val errorMessage          = s"validationService.giftAid.message.4"
    val removeNonWesternChars = removeNonWesternCharacters(previouslyOverClaimedAmountCell)
    val normalized            = removeNonWesternChars.trim.replace(",", "")

    if (normalized.isEmpty) Right(None)
    else {
      Try(BigDecimal(normalized)).toOption match {
        case Some(amount) =>
          val scaleOk        = amount.scale == 2
          val digitCnt       = amount.bigDecimal.unscaledValue.abs.toString.length
          val digitsOk       = digitCnt <= 15
          val positiveNumber = amount.signum > 0
          if !scaleOk || !digitsOk || !positiveNumber then Left(ValidationError(fieldKey, errorMessage))
          else Right(Some(amount))
        case None => Left(ValidationError(fieldKey, errorMessage))
      }
    }
  }

  private def validateAggregateDonations(
    raw: String,
    index: Int
  ): V[Option[String]] = {
    val input = removeNonWesternCharacters(raw, true)

    val field      = s"aggregateDonations[$index]"
    val tooLongMsg = "validationService.giftAid.message.5"

    if (input.isEmpty) {
      None.validNel
    } else if (input.length > 35) {
      invalid(field, s"$tooLongMsg")
    } else {
      Some(input).validNel
    }
  }

  private def validateItem(raw: String, index: Int): V[Int] = {
    val field = s"item[$index]"
    val input = removeNonWesternCharacters(raw)
    val error = s"validationService.giftAid.message.6"

    val formatValid = input.matches("^[1-9][0-9]{0,3}$")

    if (!formatValid) {
      invalid(field, error)
    } else {
      input.toIntOption match {
        case Some(n) if n >= 1 && n <= 1000 =>
          n.validNel
        case _ =>
          invalid(field, error)
      }
    }
  }

  private def validateDate(raw: String, index: Int, today: LocalDate): V[LocalDate] = {
    val field = s"donationDate[$index]"
    val input = removeNonWesternCharacters(raw)

    if input.isEmpty then
      invalid(
        field,
        "validationService.giftAid.message.7"
      )
    else {
      Try(LocalDate.parse(input, dateFormatter)).toOption match {
        case None =>
          invalid(
            field,
            "validationService.giftAid.message.8"
          )
        case Some(d) if d.isAfter(today) =>
          invalid(
            field,
            "validationService.giftAid.message.9"
          )
        case Some(d) =>
          d.validNel
      }
    }
  }

  private def validateMoney(raw: String, index: Int, isAggregated: Boolean): V[BigDecimal] = {
    val field = s"donationAmount[$index]"
    val input = removeNonWesternCharacters(raw)

    def invalidFormat = invalid(
      field,
      "validationService.giftAid.message.10"
    )

    def missing = invalid(
      field,
      "validationService.giftAid.message.11"
    )

    def aggregateOverLimit = invalid(
      field,
      "validationService.giftAid.message.12"
    )

    if (input.isEmpty) {
      missing
    } else {
      val normalized = input.replace(",", "")

      Try(BigDecimal(normalized)).toOption match {
        case None =>
          invalidFormat
        case Some(amount) =>
          val scaleOk  = amount.scale == 2
          val digitCnt = amount.bigDecimal.unscaledValue.abs.toString.length
          val digitsOk = digitCnt <= 15

          if (!scaleOk || !digitsOk) {
            invalidFormat
          } else {
            val min = BigDecimal("0.01")
            if (isAggregated) {
              if (amount < min) invalidFormat
              else if (amount > BigDecimal("1000")) aggregateOverLimit
              else amount.validNel
            } else {
              val max = BigDecimal("9999999999999.99")
              if (amount < min || amount > max) invalidFormat
              else amount.validNel
            }
          }
      }
    }
  }

  private def validateTitle(
    raw: String,
    index: Int,
    isAggregated: Boolean
  ): V[Option[String]] = {
    val field = s"donorTitle[$index]"
    val input = removeNonWesternCharacters(raw)

    if (!isAggregated) {
      if (input.isEmpty) {
        None.validNel
      } else if ("^[A-Za-z][A-Za-z'-]{1,3}$".r.matches(input)) {
        Some(input).validNel
      } else {
        invalid(
          field,
          "validationService.giftAid.message.13"
        )
      }
    } else None.validNel
  }

  private def validateName(
    raw: String,
    fieldLabel: String,
    fieldKey: String,
    isAggregated: Boolean
  ): V[Option[String]] = {
    val input = removeNonWesternCharacters(raw, true)

    if (!isAggregated) {
      if (input.isEmpty) {
        invalid(
          fieldKey,
          if fieldLabel.toLowerCase.contains("first name") then s"validationService.giftAid.message.14"
          else s"validationService.giftAid.message.15"
        )
      } else if (input.length > 35) {
        invalid(
          fieldKey,
          if fieldLabel.toLowerCase.contains("first name") then s"validationService.giftAid.message.16"
          else s"validationService.giftAid.message.17"
        )
      } else {
        Some(input).validNel
      }
    } else None.validNel
  }

  private def validateHouseName(
    raw: String,
    index: Int,
    isAggregated: Boolean
  ): V[Option[String]] = {
    val field = s"donorHouse[$index]"
    val input = removeNonWesternCharacters(raw, true)

    if (!isAggregated) {
      if (input.isEmpty) {
        ValidationError(
          field,
          "validationService.giftAid.message.18"
        ).invalidNel
      } else if (input.length > 40) {
        ValidationError(
          field,
          "validationService.giftAid.message.19"
        ).invalidNel
      } else {
        Some(input).validNel
      }
    } else None.validNel
  }

  private def validatePostcode(
    raw: String,
    index: Int,
    isAggregated: Boolean
  ): V[Option[String]] = {
    val field     = s"postcode[$index]"
    val input     = removeNonWesternCharacters(raw.trim)
    val postCodeU = input.toUpperCase

    val isValidFormat = input == "X" || postCodeU.matches(
      "^(GIR 0AA)|((([A-Z][0-9][0-9]?)|(([A-Z][A-HJ-Y][0-9][0-9]?)|(([A-Z][0-9][A-Z])|([A-Z][A-HJ-Y][0-9][A-Z])))) [0-9][A-Z]{2})$"
    )
    if (!isAggregated) {
      if (input.isEmpty) {
        ValidationError(
          field,
          "validationService.giftAid.message.20"
        ).invalidNel
      } else if (isValidFormat) {
        Some(postCodeU).validNel
      } else {
        ValidationError(
          field,
          "validationService.giftAid.message.21"
        ).invalidNel
      }
    } else None.validNel
  }

  private def validateSponsoredEvent(
    raw: String,
    index: Int,
    isAggregated: Boolean
  ): V[Boolean] = {
    val field = s"sponsoredEvent[$index]"
    val input = removeNonWesternCharacters(raw.trim.toLowerCase)
    if (!isAggregated) {
      if (input.isEmpty) {
        false.validNel
      } else if (input == "yes") {
        true.validNel
      } else {
        invalid(
          field,
          "validationService.giftAid.message.22"
        )
      }
    } else false.validNel
  }

  private def validateAggregateDonationRules(row: GiftAidDonationRow, index: Int): V[Unit] = {
    val field        = s"aggregateDonationsConflict[$index]"
    val donationType = DonationType.fromRow(row)

    if (!donationType.isAggregated) {
      ().validNel
    } else {
      val rules: List[(Boolean, String)] = List(
        row.sponsoredEvent.trim.nonEmpty -> s"validationService.giftAid.message.23",
        row.donorTitle.trim.nonEmpty     -> s"validationService.giftAid.message.24",
        row.donorFirstName.trim.nonEmpty -> s"validationService.giftAid.message.25",
        row.donorLastName.trim.nonEmpty  -> s"validationService.giftAid.message.26",
        row.donorHouse.trim.nonEmpty     -> s"validationService.giftAid.message.27",
        row.donorPostcode.trim.nonEmpty  -> s"validationService.giftAid.message.28"
      )

      val errors = rules.filter(_._1).map(_._2)
      if (errors.isEmpty) {
        ().validNel
      } else {
        errors.map(msg => ValidationError(field, msg)).toNel.fold(().validNel)(_.invalid)
      }
    }
  }

  private def invalid(field: String, msg: String): V[Nothing] =
    ValidationError(field, msg).invalidNel

}
