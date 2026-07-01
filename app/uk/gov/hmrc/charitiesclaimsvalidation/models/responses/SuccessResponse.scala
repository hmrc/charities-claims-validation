/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.responses

import play.api.libs.json.{Json, OFormat}

final case class SuccessResponse(success: Boolean)

object SuccessResponse {
  given format: OFormat[SuccessResponse] = Json.format[SuccessResponse]
}
