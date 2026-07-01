/*
 * Copyright 2025 HM Revenue & Customs
 *
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
