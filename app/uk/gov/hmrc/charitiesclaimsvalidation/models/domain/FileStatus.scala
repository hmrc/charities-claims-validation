/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import play.api.libs.json.*

sealed trait FileStatus { def value: String }

object FileStatus {
  case object AwaitingUpload extends FileStatus { val value = "AWAITING_UPLOAD" }

  case object Verifying extends FileStatus { val value = "VERIFYING" }

  case object VerificationFailed extends FileStatus { val value = "VERIFICATION_FAILED" }

  case object Validating extends FileStatus { val value = "VALIDATING" }

  case object Validated extends FileStatus { val value = "VALIDATED" }

  case object ValidationFailed extends FileStatus { val value = "VALIDATION_FAILED" }

  implicit val format: Format[FileStatus] = new Format[FileStatus] {
    override def writes(status: FileStatus): JsValue = JsString(status.value)

    override def reads(json: JsValue): JsResult[FileStatus] = json match {
      case JsString("AWAITING_UPLOAD")     => JsSuccess(AwaitingUpload)
      case JsString("VERIFYING")           => JsSuccess(Verifying)
      case JsString("VERIFICATION_FAILED") => JsSuccess(VerificationFailed)
      case JsString("VALIDATING")          => JsSuccess(Validating)
      case JsString("VALIDATED")           => JsSuccess(Validated)
      case JsString("VALIDATION_FAILED")   => JsSuccess(ValidationFailed)
      case _                               => JsError("Invalid FileStatus")
    }
  }
}
