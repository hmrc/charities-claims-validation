/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.responses

import play.api.libs.json.{Json, Writes}
import play.api.mvc.Results
import play.api.mvc.Result

object ErrorResults {

  implicit val errorResponseWrites: Writes[ErrorResponse] = Writes { e =>
    val json = Json.obj(
      "error"   -> e.error,
      "message" -> e.message
    )
    e.success.fold(json)(s => json + ("success" -> Json.toJson(s)))
  }

  def badRequest(error: ErrorResponse): Result          = Results.BadRequest(Json.toJson(error))
  def notFound(error: ErrorResponse): Result            = Results.NotFound(Json.toJson(error))
  def internalServerError(error: ErrorResponse): Result = Results.InternalServerError(Json.toJson(error))
}
