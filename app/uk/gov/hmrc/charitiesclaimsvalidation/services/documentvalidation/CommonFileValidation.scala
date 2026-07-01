/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation

import cats.effect.IO
import play.api.Logging
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{ValidationError, ValidationType}
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.UpscanSuccessRequest
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.CommonFileValidation.*
import eu.timepit.refined.auto.autoUnwrap
import org.w3c.dom.Document
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.errors.BadSheetNameException

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

// validationService.commonFile.message.1 = The selected file could not be uploaded
// validationService.commonFile.message.2 = There is a problem with your spreadsheet
// validationService.commonFile.message.3 = The selected file must use the template required

class CommonFileValidation @Inject() ()(using ec: ExecutionContext) extends Logging {

  def validateFile(up: UpscanSuccessRequest, vt: ValidationType): Future[Either[ValidationError, Unit]] = Future {
    val fileName = up.uploadDetails.fileName
    val mimeType = up.uploadDetails.fileMimeType
    if (isBlankFileName(up)) {
      logger.warn(s"File validation failed: blank file name, validationType=${vt.toString}")
      Left(FileNameIsBlank)
    } else if (isEmptyFile(up)) {
      logger.warn(s"File validation failed: empty file, fileName=$fileName, validationType=${vt.toString}")
      Left(FileIsEmpty)
    } else if (!isOdsMimeType(up) || !hasOdsExtension(up)) {
      logger.warn(s"File validation failed: invalid file type, fileName=$fileName, mimeType=$mimeType, validationType=${vt.toString}")
      Left(FileIsInvalidType)
    } else {
      Right(())
    }
  }
}

object CommonFileValidation {
  private val OdsMimeType  = "application/vnd.oasis.opendocument.spreadsheet"
  private val OdsExtension = ".ods"

  // this is left as-is since validation done via the front-end
  val FileIsEmpty: ValidationError =
    ValidationError("fileError", "ERROR: The file specified is not an ODF (Open Document Format) file. Please attach a valid file to continue.")

  // this is left as-is since validation done via the front-end
  val FileNameIsBlank: ValidationError =
    ValidationError(
      "fileError",
      "ERROR: You must either select a file to attach or tick the checkbox declaring you do not want to attach a schedule at this time."
    )

  // this is left as-is since validation done via the front-end
  val FileIsInvalidType: ValidationError =
    ValidationError(
      "fileError",
      "ERROR: The file specified is not an ODF (Open Document Format) spreadsheet (.ods). Please attach a valid file to continue."
    )

  val spreadsheetFileNotFound =
    ValidationError(
      "fileError",
      "validationService.commonFile.message.1"
    )

  // this is left as-is since validation done via the front-end
  val spreadsheetUnexpectedError =
    ValidationError(
      "fileError",
      "validationService.commonFile.message.2"
    )

  val sheetNameIsDifferent =
    ValidationError(
      "fileError",
      "validationService.commonFile.message.3"
    )

  def verifySheetName(doc: Document, validationType: ValidationType): IO[String] = {
    for {
      sheetName <- OdsReaderService.extractSheetName(doc)
      isValid = isCorrectSheetName(sheetName, validationType)
      _ <- IO.raiseUnless(isValid)(BadSheetNameException())
    } yield sheetName
  }

  def isCorrectSheetName(sheetName: String, validationType: ValidationType): Boolean = {
    val name = sheetName.trim

    validationType match {
      case ValidationType.GiftAid =>
        name.startsWith("R68GAD_V1_00_0")

      case ValidationType.OtherIncome =>
        name.startsWith("R68OI_V1_00_0")

      case ValidationType.CommunityBuildings =>
        name.startsWith("R68CB_V1_00_0")

      case ValidationType.ConnectedCharities =>
        name.startsWith("R68CC_V1_00_0")
    }
  }

  // Address this removal in phase 2, it should throw an error
  def removeNonWesternCharacters(inputStr: String, normalisingSpacing: Boolean = false): String = {
    val strBeforeNormalisingSpacing = inputStr.replaceAll("[^\\x20-\\x7E\\xA1-\\xFF]", "").trim

    if normalisingSpacing then strBeforeNormalisingSpacing.replaceAll("\\s+", " ")
    else strBeforeNormalisingSpacing
  }

  private def isOdsMimeType(up: UpscanSuccessRequest): Boolean =
    up.uploadDetails.fileMimeType.equals(OdsMimeType)

  private def hasOdsExtension(up: UpscanSuccessRequest): Boolean =
    up.uploadDetails.fileName.toLowerCase.endsWith(OdsExtension)

  private def isEmptyFile(up: UpscanSuccessRequest): Boolean =
    up.uploadDetails.size == 0

  private def isBlankFileName(up: UpscanSuccessRequest): Boolean =
    up.uploadDetails.fileName.trim.isEmpty

}
