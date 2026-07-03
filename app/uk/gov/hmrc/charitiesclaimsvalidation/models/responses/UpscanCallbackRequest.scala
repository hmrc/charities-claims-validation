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

package uk.gov.hmrc.charitiesclaimsvalidation.models.responses

import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.FailureDetails
import eu.timepit.refined.types.all.NonEmptyString
import uk.gov.hmrc.charitiesclaimsvalidation.models.formats.JsonImplicits.{*, given}

import java.time.Instant

final case class UploadDetails(
  fileName: NonEmptyString,
  fileMimeType: NonEmptyString,
  uploadTimestamp: Instant,
  checksum: NonEmptyString,
  size: Long
)

object UploadDetails {
  implicit val format: OFormat[UploadDetails] = Json.format[UploadDetails]
}

sealed trait UpscanCallbackRequest {
  def reference: NonEmptyString
  def fileStatus: NonEmptyString
}

case class UpscanSuccessRequest(
  reference: NonEmptyString,
  downloadUrl: NonEmptyString,
  fileStatus: NonEmptyString,
  uploadDetails: UploadDetails
) extends UpscanCallbackRequest

object UpscanSuccessRequest {
  implicit val format: OFormat[UpscanSuccessRequest] = Json.format[UpscanSuccessRequest]
}

case class UpscanFailureRequest(
  reference: NonEmptyString,
  fileStatus: NonEmptyString,
  failureDetails: FailureDetails
) extends UpscanCallbackRequest

object UpscanFailureRequest {
  implicit val format: OFormat[UpscanFailureRequest] = Json.format[UpscanFailureRequest]
}

object UpscanCallbackRequest {
  implicit val reads: Reads[UpscanCallbackRequest] = (json: JsValue) => {
    (json \ "fileStatus").asOpt[String] match {
      case Some("READY")  => Json.fromJson[UpscanSuccessRequest](json)
      case Some("FAILED") => Json.fromJson[UpscanFailureRequest](json)
      case _              => JsError("Invalid or missing fileStatus. Expected 'READY' or 'FAILED'")
    }
  }
}
