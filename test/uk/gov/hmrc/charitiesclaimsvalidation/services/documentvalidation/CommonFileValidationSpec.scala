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

import eu.timepit.refined.types.all.NonEmptyString
import org.scalatest.time.{Millis, Seconds, Span}
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.ValidationType
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.{UploadDetails, UpscanSuccessRequest}
import uk.gov.hmrc.charitiesclaimsvalidation.util.BaseSpec

import java.time.Instant

class CommonFileValidationSpec extends BaseSpec {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(3, Seconds), interval = Span(50, Millis))

  private val validator = new CommonFileValidation()

  private val odsMime = "application/vnd.oasis.opendocument.spreadsheet"

  private def mkUpscan(
    fileName: String,
    mimeType: String,
    size: Long,
    reference: NonEmptyString = NonEmptyString.unsafeFrom("ref-123"),
    downloadUrl: NonEmptyString = NonEmptyString.unsafeFrom("http://example.com/file"),
    fileStatus: NonEmptyString = NonEmptyString.unsafeFrom("READY")
  ): UpscanSuccessRequest =
    UpscanSuccessRequest(
      reference = reference,
      downloadUrl = downloadUrl,
      fileStatus = fileStatus,
      uploadDetails = UploadDetails(
        fileName = NonEmptyString.unsafeFrom(fileName),
        fileMimeType = NonEmptyString.unsafeFrom(mimeType),
        uploadTimestamp = Instant.now,
        checksum = NonEmptyString.unsafeFrom("somechecksum"),
        size = size
      )
    )

  "CommonFileValidation.removeNonWesternCharacters" - {

    "returns empty string for blank-looking inputs" in {
      val blankInputs = List(
        "–",            // en dash
        "—",            // em dash
        "…",            // ellipsis
        "\u00A0",       // non-breaking space
        "– –",          // en dash + space + en dash (space residue after removal)
        "— —",          // em dash + space + em dash
        "\u2018 \u2019" // left/right single quotation marks around a space
      )
      blankInputs.foreach { input =>
        withClue(s"Expected empty string for input: ") {
          CommonFileValidation.removeNonWesternCharacters(input) shouldBe ""
        }
      }
    }

    "strips non-western characters while preserving valid content" in {
      CommonFileValidation.removeNonWesternCharacters("Hello World") shouldBe "Hello World"
      CommonFileValidation.removeNonWesternCharacters("☺Hello") shouldBe "Hello"
      CommonFileValidation.removeNonWesternCharacters("Hello☺") shouldBe "Hello"
      CommonFileValidation.removeNonWesternCharacters("☺1") shouldBe "1"
    }

    "trims surrounding whitespace after removal" in {
      CommonFileValidation.removeNonWesternCharacters("  hello  ") shouldBe "hello"
      CommonFileValidation.removeNonWesternCharacters("–hello–") shouldBe "hello"
    }
  }

  "CommonFileValidation.validateFile" - {

    "returns FileNameIsBlank when file name is blank" in {
      val up = mkUpscan(fileName = "   ", mimeType = odsMime, size = 10)

      whenReady(validator.validateFile(up, ValidationType.OtherIncome)) { res =>
        res shouldBe Left(CommonFileValidation.FileNameIsBlank)
      }
    }

    "returns FileIsEmpty when size == 0" in {
      val up = mkUpscan(fileName = "anything.ods", mimeType = odsMime, size = 0)

      whenReady(validator.validateFile(up, ValidationType.OtherIncome)) { res =>
        res shouldBe Left(CommonFileValidation.FileIsEmpty)
      }
    }

    "returns FileIsInvalidType when MIME type is not ODS" in {
      val up = mkUpscan(fileName = "anything.ods", mimeType = "application/pdf", size = 10)

      whenReady(validator.validateFile(up, ValidationType.OtherIncome)) { res =>
        res shouldBe Left(CommonFileValidation.FileIsInvalidType)
      }
    }

    "returns FileIsInvalidType when extension is not .ods even if MIME is ODS" in {
      val up = mkUpscan(fileName = "anything.xlsx", mimeType = odsMime, size = 10)

      whenReady(validator.validateFile(up, ValidationType.OtherIncome)) { res =>
        res shouldBe Left(CommonFileValidation.FileIsInvalidType)
      }
    }

    "returns Right(()) when file matches an OtherIncome template name and is valid ODS" in {
      val templateName = "other_income_schedule_Libre.ods"
      val up           = mkUpscan(fileName = templateName, mimeType = odsMime, size = 10)

      whenReady(validator.validateFile(up, ValidationType.OtherIncome)) { res =>
        res shouldBe Right(())
      }
    }

    "returns Right(()) when file matches a CommunityBuildings template name and is valid ODS" in {
      val templateName = "community_buildings_schedule__Libre_ (1).ods"
      val up           = mkUpscan(fileName = templateName, mimeType = odsMime, size = 10)

      whenReady(validator.validateFile(up, ValidationType.CommunityBuildings)) { res =>
        res shouldBe Right(())
      }
    }
  }
}
