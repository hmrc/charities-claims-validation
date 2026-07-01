/*
 * Copyright 2025 HM Revenue & Customs
 *
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
