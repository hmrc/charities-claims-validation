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

import play.api.libs.json.{Json, OWrites, Writes}
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{AwaitingUploadStatus, ClaimValidationStatus, ValidationType}
import cats.syntax.all.catsSyntaxTuple2Semigroupal

case class UploadSummaryItem(
  reference: String,
  validationType: ValidationType,
  fileStatus: String,
  uploadUrl: Option[String] = None,
  fields: Option[Map[String, String]] = None
)

object UploadSummaryItem {

  def fromClaimValidationStatus(status: ClaimValidationStatus): UploadSummaryItem =
    status match {
      case s: AwaitingUploadStatus =>
        UploadSummaryItem(
          reference = s.reference,
          validationType = s.validationType,
          fileStatus = s.fileStatus,
          uploadUrl = Some(s.uploadUrl),
          fields = s.fields
        )
      case s =>
        UploadSummaryItem(
          reference = s.reference,
          validationType = s.validationType,
          fileStatus = s.fileStatus
        )
    }

  implicit val writes: OWrites[UploadSummaryItem] = (item: UploadSummaryItem) => {
    val base = Json.obj(
      "reference"      -> item.reference,
      "validationType" -> item.validationType.toString,
      "fileStatus"     -> item.fileStatus
    )
    (item.uploadUrl, item.fields).mapN((url, fields) => base + ("uploadUrl" -> Json.toJson(url)) + ("fields" -> Json.toJson(fields))).getOrElse(base)
  }
}

case class UploadSummaryResponse(uploads: Seq[UploadSummaryItem])

object UploadSummaryResponse {
  implicit val writes: OWrites[UploadSummaryResponse] = Json.writes[UploadSummaryResponse]
}
