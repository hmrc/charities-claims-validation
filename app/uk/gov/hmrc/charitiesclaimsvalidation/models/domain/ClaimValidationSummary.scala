/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import play.api.libs.json.{Json, OFormat}

case class ClaimValidationSummary(
  claimId: String,
  uploads: Seq[ClaimValidationStatus]
)

object ClaimValidationSummary {
  implicit val format: OFormat[ClaimValidationSummary] = Json.format[ClaimValidationSummary]
}
