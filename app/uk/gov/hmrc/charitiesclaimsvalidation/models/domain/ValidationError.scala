/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import play.api.libs.json.{Json, OFormat}

final case class ValidationError(
  field: String,
  error: String
)

object ValidationError {
  implicit val format: OFormat[ValidationError] = Json.format[ValidationError]
}
