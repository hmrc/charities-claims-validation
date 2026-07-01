/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation

import cats.data.{Validated, ValidatedNel}
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.{domain, *}
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{CommunityBuilding, CommunityBuildingData, ValidationError, ValidationType}
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.errors.{BadSheetNameException, NoRowsFoundException}
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.CommunityBuildingRow
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.CommonFileValidation.{removeNonWesternCharacters, sheetNameIsDifferent, spreadsheetFileNotFound, spreadsheetUnexpectedError, verifySheetName}

import java.io.FileNotFoundException
import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import play.api.Logging
import scala.concurrent.Future
import scala.util.Try

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

@Singleton()
class CommunityBuildingValidationService @Inject() ()(using ioRuntime: IORuntime) extends Logging {

  def validate(path: String): Future[(List[ValidationError], Option[CommunityBuildingData])] =
    validateRowsIO(path).unsafeToFuture()

  private def validateRowsIO(downloadUrl: String): IO[(List[ValidationError], Option[CommunityBuildingData])] =
    OdsReaderService
      .withDocumentStream(downloadUrl) { doc =>
        for {
          sheetName                         <- verifySheetName(doc, ValidationType.CommunityBuildings)
          rawCommunityBuildingRowsWithIndex <- OdsReaderService.rowsFromDocumentWithIndex[CommunityBuildingRow](doc, CommunityBuildingRow.layout)
          _                                 <- IO.raiseWhen(rawCommunityBuildingRowsWithIndex.isEmpty)(NoRowsFoundException)

          communityBuildingRowWithIndex = rawCommunityBuildingRowsWithIndex.map { case (idx, row) =>
            CommunityBuildingValidationService.BuildingRowWithIndex(idx, row)
          }
          (rowErrors, validatedBuildingsWithIndex) = CommunityBuildingValidationService.validateRows(
            communityBuildingRowWithIndex,
            today = LocalDate.now
          )
          crossFieldErrors        = CommunityBuildingValidationService.validateCrossField(validatedBuildingsWithIndex)
          allErrors               = CommunityBuildingValidationService.sortErrorsByField(rowErrors ++ crossFieldErrors)
          validCommunityBuildings = validatedBuildingsWithIndex.map(_.building)
          totalOfAllAmounts = Option.when(validCommunityBuildings.nonEmpty) {
            validCommunityBuildings.foldLeft(BigDecimal(0)) { (tot, row) =>
              tot + row.amountYear1 + row.amountYear2.getOrElse(BigDecimal(0))
            }
          }
        } yield (allErrors, Some(domain.CommunityBuildingData(totalOfAllAmounts, validCommunityBuildings)))
      }
      .recover {
        case NoRowsFoundException =>
          logger.warn("CommunityBuildings validation failed: spreadsheet contains no data")
          (List(CommunityBuildingValidationService.spreadsheetContainsNoData), None)
        case _: FileNotFoundException =>
          logger.warn("CommunityBuildings validation failed: spreadsheet file not found")
          (List(spreadsheetFileNotFound), None)
        case _: BadSheetNameException =>
          logger.warn("CommunityBuildings validation failed: incorrect sheet name")
          (List(sheetNameIsDifferent), None)
        case ex =>
          logger.error(s"CommunityBuildings validation failed with unexpected error: ${ex.getMessage}", ex)
          (List(spreadsheetUnexpectedError), None)
      }

}

object CommunityBuildingValidationService {

  type V[A] = ValidatedNel[ValidationError, A]
  private val moneyMin = BigDecimal("0.01")
  private val moneyMax = BigDecimal("9999999999999.99")

  private val spreadsheetContainsNoData =
    ValidationError(
      "communityBuildings",
      "validationService.communityBuildings.message.1"
    )

  def validateRows(inputRows: List[BuildingRowWithIndex], today: LocalDate): (List[ValidationError], List[ValidatedBuildingWithIndex]) = {
    val validated: List[V[ValidatedBuildingWithIndex]] = inputRows.map(validateRow(_, today))

    val errors = validated.collect { case Validated.Invalid(errs) => errs.toList }.flatten
    val valids = validated.collect { case Validated.Valid(row) => row }

    (errors, valids)
  }

  private def validateRow(buildingRowWithIndex: BuildingRowWithIndex, today: LocalDate): V[ValidatedBuildingWithIndex] = {
    import buildingRowWithIndex.*

    val errorIndex = index - CommunityBuildingRow.layout.rowRange.start

    val itemV     = validateItem(row.item, errorIndex)
    val nameV     = validateBuildingName(row.buildingName, errorIndex)
    val addressV  = validateFirstLineOfAddress(row.firstLineOfAddress, errorIndex)
    val postcodeV = validatePostcode(row.postcode, errorIndex)

    val taxYear1V = validateTaxYear(row.taxYear1, "First", errorIndex, today, required = true)
    val amount1V  = validateAmount(row.amount1, "First", errorIndex, required = true)

    val taxYear2ConditionalV = validateTaxYear2Conditional(row.taxYear1, row.taxYear2, row.amount2, errorIndex, today)

    (itemV, nameV, addressV, postcodeV, taxYear1V, amount1V, taxYear2ConditionalV).mapN { (item, name, address, postcode, year1, amt1, year2Opt) =>
      ValidatedBuildingWithIndex(
        errorIndex = errorIndex,
        building = CommunityBuilding(
          communityBuildingItem = item,
          buildingName = name,
          firstLineOfAddress = address,
          postcode = postcode,
          taxYear1 = year1,
          amountYear1 = amt1,
          taxYear2 = year2Opt.map(_._1),
          amountYear2 = year2Opt.map(_._2)
        )
      )
    }
  }

  private def validateItem(raw: String, index: Int): V[Int] = {
    val field = s"item[$index]"
    val t     = removeNonWesternCharacters(raw)

    if !t.matches("^[1-9][0-9]{0,2}$") then
      invalid(
        field,
        s"validationService.communityBuildings.message.2"
      )
    else {
      val value = t.toInt
      if value < 1 || value > 500 then
        invalid(
          field,
          s"validationService.communityBuildings.message.2"
        )
      else value.validNel
    }
  }

  private def validateBuildingName(raw: String, index: Int): V[String] = {
    val field = s"buildingName[$index]"
    val t     = removeNonWesternCharacters(raw, true)

    if t.isEmpty then
      invalid(
        field,
        s"validationService.communityBuildings.message.3"
      )
    else if t.length > 160 then
      invalid(
        field,
        s"validationService.communityBuildings.message.4"
      )
    else t.validNel
  }

  private def validateFirstLineOfAddress(raw: String, index: Int): V[String] = {
    val field = s"firstLineOfAddress[$index]"
    val t     = removeNonWesternCharacters(raw, true)

    if t.isEmpty then
      invalid(
        field,
        s"validationService.communityBuildings.message.5"
      )
    else if t.length > 40 then
      invalid(
        field,
        s"validationService.communityBuildings.message.6"
      )
    else t.validNel
  }

  private def validatePostcode(raw: String, index: Int): V[String] = {
    val field = s"postcode[$index]"
    val t     = removeNonWesternCharacters(raw)

    val postcodePattern =
      "^(GIR 0AA)|((([A-Z][0-9][0-9]?)|(([A-Z][A-HJ-Y][0-9][0-9]?)|(([A-Z][0-9][A-Z])|([A-Z][A-HJ-Y][0-9][A-Z])))) [0-9][A-Z]{2})$"

    if t.isEmpty then
      invalid(
        field,
        s"validationService.communityBuildings.message.7"
      )
    else if !t.toUpperCase.matches(postcodePattern) then
      invalid(
        field,
        s"validationService.communityBuildings.message.8"
      )
    else t.validNel
  }

  private def validateTaxYear(
    raw: String,
    label: String,
    index: Int,
    today: LocalDate,
    required: Boolean
  ): V[Int] = {

    val field = s"taxYear$label[$index]"
    val t     = removeNonWesternCharacters(raw)

    if t.isEmpty then {
      if required then
        invalid(
          field,
          if label.toLowerCase.contains("first") then s"validationService.communityBuildings.message.9"
          else s"validationService.communityBuildings.message.10"
        )
      else 0.validNel // Unused placeholder - validateTaxYear2Conditional discards this when both fields empty
    } else if !t.matches("^\\d{4}$") || !t.toIntOption.exists(_ >= 1) then {
      invalid(
        field,
        if label.toLowerCase.contains("first") then s"validationService.communityBuildings.message.11"
        else s"validationService.communityBuildings.message.12"
      )
    } else {
      val year           = t.toInt
      val currentTaxYear = getCurrentTaxYear(today)
      val earliestYear   = currentTaxYear - 3

      val errs: List[ValidationError] = List(
//        Option.when(year < GASDS_Min_Year)(
//          ValidationError(
//            field,
//            s"validationService.communityBuildings.message.13"
//          )
//        ),
        Option.when(year > currentTaxYear)(
          ValidationError(
            field,
            s"validationService.communityBuildings.message.14"
          )
        ),
        Option.when(year < earliestYear)(
          ValidationError(
            field,
            s"validationService.communityBuildings.message.15"
          )
        )
      ).flatten

      errs.toNel match {
        case Some(nel) => nel.invalid[Int]
        case None      => year.validNel
      }
    }
  }

  private def validateAmount(
    raw: String,
    label: String,
    index: Int,
    required: Boolean
  ): V[BigDecimal] = {
    val field = s"amount$label[$index]"
    val t     = removeNonWesternCharacters(raw)

    if t.isEmpty then {
      if required then
        invalid(
          field,
          if label.toLowerCase.contains("first") then s"validationService.communityBuildings.message.16"
          else s"validationService.communityBuildings.message.17"
        )
      else BigDecimal(0).validNel // Unused placeholder - validateTaxYear2Conditional discards this when both fields empty
    } else {
      val normalized = t.replace(",", "")
      Try(BigDecimal(normalized)).toOption match {
        case None =>
          invalid(
            field,
            if label.toLowerCase.contains("first") then s"validationService.communityBuildings.message.18"
            else s"validationService.communityBuildings.message.19"
          )

        case Some(amount) =>
          val scaleOk  = amount.scale == 2
          val digitCnt = amount.bigDecimal.unscaledValue.abs.toString.length
          val digitsOk = digitCnt <= 15
          val inRange  = amount >= moneyMin && amount <= moneyMax

          val errs: List[ValidationError] = List(
            Option.when(!scaleOk)(
              ValidationError(
                field,
                if label.toLowerCase.contains("first") then s"validationService.communityBuildings.message.18"
                else s"validationService.communityBuildings.message.19"
              )
            ),
            Option.when(!digitsOk)(
              ValidationError(
                field,
                if label.toLowerCase.contains("first") then s"validationService.communityBuildings.message.18"
                else s"validationService.communityBuildings.message.19"
              )
            ),
            Option.when(!inRange)(
              ValidationError(
                field,
                if label.toLowerCase.contains("first") then s"validationService.communityBuildings.message.18"
                else s"validationService.communityBuildings.message.19"
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

  private def validateTaxYear2Conditional(
    taxYear1Raw: String,
    taxYear2Raw: String,
    amount2Raw: String,
    index: Int,
    today: LocalDate
  ): V[Option[(Int, BigDecimal)]] = {
    val year2Empty   = taxYear2Raw.trim.isEmpty
    val amount2Empty = amount2Raw.trim.isEmpty

    (year2Empty, amount2Empty) match {
      case (true, true) =>
        None.validNel

      case (false, false) =>
        val year2V   = validateTaxYear(taxYear2Raw, "Second", index, today, required = true)
        val amount2V = validateAmount(amount2Raw, "Second", index, required = true)

        // Additional validation: taxYear1 and taxYear2 must be different within the same row
        val uniqueYearsV = validateTaxYearsUnique(taxYear1Raw, taxYear2Raw, index)

        (year2V, amount2V, uniqueYearsV).mapN((y, a, _) => Some((y, a)))

      case (true, false) =>
        invalid(
          s"amount2[$index]",
          s"validationService.communityBuildings.message.10"
        )

      case (false, true) =>
        invalid(
          s"taxYear2[$index]",
          s"validationService.communityBuildings.message.17"
        )
    }
  }

  private def validateTaxYearsUnique(
    taxYear1Raw: String,
    taxYear2Raw: String,
    index: Int
  ): V[Unit] = {
    val year1 = removeNonWesternCharacters(taxYear1Raw)
    val year2 = removeNonWesternCharacters(taxYear2Raw)

    if year1 == year2 then {
      invalid(
        s"taxYear2[$index]",
        s"validationService.communityBuildings.message.20"
      )
    } else {
      ().validNel
    }
  }

  def validateCrossField(buildings: List[ValidatedBuildingWithIndex]): List[ValidationError] = {
    val buildingGroups = buildings.groupBy { vb =>
      normalizeBuilding(vb.building.buildingName, vb.building.firstLineOfAddress, vb.building.postcode)
    }

    buildingGroups.flatMap { case (_, buildingsForSameBuilding) =>
      // Track tax years with their associated error indices and item numbers
      val taxYearsWithIndices: List[(Int, Int, Int)] = buildingsForSameBuilding.flatMap { vb =>
        val year1Entry = (vb.building.taxYear1, vb.errorIndex, vb.building.communityBuildingItem)
        val year2Entry = vb.building.taxYear2.map(y => (y, vb.errorIndex, vb.building.communityBuildingItem))
        year1Entry :: year2Entry.toList
      }

      // Find duplicate tax years and report the item that caused the duplicate (the later occurrence)
      val duplicateErrors = findDuplicateTaxYearErrors(taxYearsWithIndices)

      // Find items that exceed the 3 tax year limit
      val limitErrors = findTaxYearLimitErrors(taxYearsWithIndices)

      duplicateErrors ++ limitErrors
    }.toList
  }

  private def findDuplicateTaxYearErrors(
    taxYearsWithIndices: List[(Int, Int, Int)]
  ): List[ValidationError] = {
    val seenTaxYears   = scala.collection.mutable.Set[Int]()
    val duplicateItems = scala.collection.mutable.ListBuffer[(Int, Int)]() // (errorIndex, itemId)

    taxYearsWithIndices.foreach { case (taxYear, errorIndex, itemId) =>
      if seenTaxYears.contains(taxYear) then {
        duplicateItems += errorIndex -> itemId
      } else {
        seenTaxYears += taxYear
      }
    }

    duplicateItems.distinctBy(_._1).toList.map { case (errorIndex, itemId) =>
      ValidationError(
        s"building[$errorIndex]",
        s"validationService.communityBuildings.message.21"
      )
    }
  }

  private def findTaxYearLimitErrors(
    taxYearsWithIndices: List[(Int, Int, Int)]
  ): List[ValidationError] = {
    // Track unique years in the order they first appear
    val seenYears           = scala.collection.mutable.LinkedHashSet[Int]()
    val itemsExceedingLimit = scala.collection.mutable.ListBuffer[(Int, Int)]() // (errorIndex, itemId)

    taxYearsWithIndices.foreach { case (taxYear, errorIndex, itemId) =>
      if !seenYears.contains(taxYear) then {
        seenYears += taxYear
        // If this is the 4th+ unique year, the item that introduced it exceeds the limit
        if seenYears.size > 3 then {
          itemsExceedingLimit += errorIndex -> itemId
        }
      }
    }

    itemsExceedingLimit.distinctBy(_._1).toList.map { case (errorIndex, itemId) =>
      ValidationError(
        s"building[$errorIndex]",
        s"validationService.communityBuildings.message.21"
      )
    }
  }

  private def normalizeBuilding(buildingName: String, address: String, postcode: String): String = {
    val normName    = buildingName.trim.toLowerCase.replaceAll("\\s+", " ")
    val normAddress = address.trim.toLowerCase.replaceAll("\\s+", " ")
    val normPost    = postcode.trim.toLowerCase.replaceAll("\\s+", "")
    s"$normName|$normAddress|$normPost"
  }

  private def getCurrentTaxYear(today: LocalDate): Int =
    if today.isAfter(LocalDate.of(today.getYear, 4, 5)) then today.getYear + 1 else today.getYear

  private def invalid(field: String, msg: String): V[Nothing] =
    ValidationError(field, msg).invalidNel

  def sortErrorsByField(errors: List[ValidationError]): List[ValidationError] =
    errors.sortBy { error =>
      val bracketPattern = """\[(\d+)\]""".r
      bracketPattern
        .findFirstMatchIn(error.field)
        .map(_.group(1).toInt)
        .getOrElse(0)
    }

  final case class BuildingRowWithIndex(index: Int, row: CommunityBuildingRow)

  final case class ValidatedBuildingWithIndex(errorIndex: Int, building: CommunityBuilding)

}
