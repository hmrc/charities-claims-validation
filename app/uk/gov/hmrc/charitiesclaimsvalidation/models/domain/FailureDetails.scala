/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import play.api.libs.json.{Json, OFormat}

case class FailureDetails(
  failureReason: FailureReason,
  message: String
)

object FailureDetails {
  implicit val format: OFormat[FailureDetails] = Json.format[FailureDetails]
}
