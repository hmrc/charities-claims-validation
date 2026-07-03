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

package uk.gov.hmrc.charitiesclaimsvalidation.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.{UploadDetails, UpscanSuccessRequest}
import eu.timepit.refined.types.string.NonEmptyString

import java.time.Instant

class UploadDetailsSpec extends AnyWordSpec with Matchers:

  private val timestamp = Instant.now()

  "UploadDetails" should:
    "serialize and deserialize UploadDetails correctly" in:
      val request = UploadDetails(
        NonEmptyString.unsafeFrom("tst.pdf"),
        NonEmptyString.unsafeFrom("application/vnd.oasis.opendocument.spreadsheet"),
        timestamp,
        NonEmptyString.unsafeFrom("2345"),
        100
      )

      val json   = Json.toJson(request)(UploadDetails.format)
      val parsed = json.as[UploadDetails]

      parsed.uploadTimestamp shouldBe request.uploadTimestamp
      parsed.fileName shouldBe request.fileName
      parsed.fileMimeType shouldBe request.fileMimeType
      parsed.size shouldBe request.size
      parsed.checksum shouldBe request.checksum

  "UpscanSuccessRequest" should:
    "serialize and deserialize UpscanSuccessRequest correctly" in:
      val request = UpscanSuccessRequest(
        reference = NonEmptyString.unsafeFrom("ref"),
        downloadUrl = NonEmptyString.unsafeFrom("https://bucketName.s3.eu-west-2.amazonaws.com?1235676"),
        fileStatus = NonEmptyString.unsafeFrom("READY"),
        uploadDetails = UploadDetails(
          fileName = NonEmptyString.unsafeFrom("test.ods"),
          fileMimeType = NonEmptyString.unsafeFrom("application/vnd.oasis.opendocument.spreadsheet"),
          uploadTimestamp = timestamp,
          checksum = NonEmptyString.unsafeFrom("396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100"),
          size = 987
        )
      )

      val json   = Json.toJson(request)(UpscanSuccessRequest.format)
      val parsed = json.as[UpscanSuccessRequest]

      parsed.reference shouldBe request.reference
      parsed.downloadUrl shouldBe request.downloadUrl
      parsed.fileStatus shouldBe request.fileStatus
      parsed.uploadDetails shouldBe request.uploadDetails
