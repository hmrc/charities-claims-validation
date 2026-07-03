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

import eu.timepit.refined.types.all.NonEmptyString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.FailureReason
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.{UploadDetails, UpscanCallbackRequest, UpscanFailureRequest, UpscanSuccessRequest}

import java.time.Instant

class UpscanCallbackRequestSpec extends AnyWordSpec with Matchers:

  "UpscanSuccessRequest" should:
    "deserialize from JSON with fileStatus READY and valid MIME type" in:
      val json = Json.obj(
        "reference"   -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
        "downloadUrl" -> "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
        "fileStatus"  -> "READY",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "test.ods",
          "fileMimeType"    -> "application/vnd.oasis.opendocument.spreadsheet",
          "uploadTimestamp" -> "2018-04-24T09:30:00Z",
          "checksum"        -> "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
          "size"            -> 987
        )
      )

      val result = Json.fromJson[UpscanCallbackRequest](json).get

      result shouldBe a[UpscanSuccessRequest]
      val successRequest = result.asInstanceOf[UpscanSuccessRequest]
      successRequest.reference.value shouldBe "11370e18-6e24-453e-b45a-76d3e32ea33d"
      successRequest.downloadUrl.value shouldBe "https://bucketName.s3.eu-west-2.amazonaws.com?1235676"
      successRequest.fileStatus.value shouldBe "READY"
      successRequest.uploadDetails shouldBe UploadDetails(
        fileName = NonEmptyString.unsafeFrom("test.ods"),
        fileMimeType = NonEmptyString.unsafeFrom("application/vnd.oasis.opendocument.spreadsheet"),
        uploadTimestamp = Instant.parse("2018-04-24T09:30:00Z"),
        checksum = NonEmptyString.unsafeFrom("396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100"),
        size = 987
      )

    "deserialize from JSON with invalid MIME type" in:
      val json = Json.obj(
        "reference"   -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
        "downloadUrl" -> "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
        "fileStatus"  -> "READY",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "test.pdf",
          "fileMimeType"    -> "application/pdf",
          "uploadTimestamp" -> "2018-04-24T09:30:00Z",
          "checksum"        -> "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
          "size"            -> 987
        )
      )

      val result = Json.fromJson[UpscanCallbackRequest](json)
      result.isError shouldBe false

  "UpscanFailureRequest" should:
    "deserialize from JSON with fileStatus FAILED and failureReason QUARANTINE" in:
      val json = Json.obj(
        "reference"  -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
        "fileStatus" -> "FAILED",
        "failureDetails" -> Json.obj(
          "failureReason" -> "QUARANTINE",
          "message"       -> "This file has a virus"
        )
      )

      val result = Json.fromJson[UpscanCallbackRequest](json).get

      result shouldBe a[UpscanFailureRequest]
      val failureRequest = result.asInstanceOf[UpscanFailureRequest]
      failureRequest.reference.value shouldBe "11370e18-6e24-453e-b45a-76d3e32ea33d"
      failureRequest.fileStatus.value shouldBe "FAILED"
      failureRequest.failureDetails.failureReason shouldBe FailureReason.Quarantine
      failureRequest.failureDetails.message shouldBe "This file has a virus"

    "deserialize from JSON with fileStatus FAILED and failureReason REJECTED" in:
      val json = Json.obj(
        "reference"  -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
        "fileStatus" -> "FAILED",
        "failureDetails" -> Json.obj(
          "failureReason" -> "REJECTED",
          "message"       -> "MIME type application/exe is not allowed for service charities"
        )
      )

      val result = Json.fromJson[UpscanCallbackRequest](json).get

      result shouldBe a[UpscanFailureRequest]
      val failureRequest = result.asInstanceOf[UpscanFailureRequest]
      failureRequest.failureDetails.failureReason shouldBe FailureReason.Rejected
      failureRequest.failureDetails.message shouldBe "MIME type application/exe is not allowed for service charities"

    "deserialize from JSON with fileStatus FAILED and failureReason UNKNOWN" in:
      val json = Json.obj(
        "reference"  -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
        "fileStatus" -> "FAILED",
        "failureDetails" -> Json.obj(
          "failureReason" -> "UNKNOWN",
          "message"       -> "Something unknown happened"
        )
      )

      val result = Json.fromJson[UpscanCallbackRequest](json).get

      result shouldBe a[UpscanFailureRequest]
      val failureRequest = result.asInstanceOf[UpscanFailureRequest]
      failureRequest.failureDetails.failureReason shouldBe FailureReason.Unknown
      failureRequest.failureDetails.message shouldBe "Something unknown happened"

  "UpscanCallbackRequest" should:
    "fail to deserialize with invalid fileStatus" in:
      val json = Json.obj(
        "reference"  -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
        "fileStatus" -> "INVALID"
      )

      val result = Json.fromJson[UpscanCallbackRequest](json)
      result.isError shouldBe true
