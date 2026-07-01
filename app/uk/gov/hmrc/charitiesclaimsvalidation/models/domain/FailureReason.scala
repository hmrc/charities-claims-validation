/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import play.api.libs.json.*

sealed trait FailureReason

object FailureReason {
  case object Quarantine extends FailureReason

  case object Rejected extends FailureReason

  case object Unknown extends FailureReason

  implicit val format: Format[FailureReason] = new Format[FailureReason] {
    override def writes(reason: FailureReason): JsValue = reason match {
      case Quarantine => JsString("QUARANTINE")
      case Rejected   => JsString("REJECTED")
      case Unknown    => JsString("UNKNOWN")
    }

    override def reads(json: JsValue): JsResult[FailureReason] = json match {
      case JsString("QUARANTINE") => JsSuccess(Quarantine)
      case JsString("REJECTED")   => JsSuccess(Rejected)
      case JsString("UNKNOWN")    => JsSuccess(Unknown)
      case _                      => JsError("Invalid FailureReason")
    }
  }
}
