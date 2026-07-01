/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.DocumentRow

final case class ConnectedCharitiesData(
  charities: List[Charity]
)

object ConnectedCharitiesData {
  implicit val format: OFormat[ConnectedCharitiesData] = Json.format[ConnectedCharitiesData]

  val fileNames = List("connected_charities_schedule__Libre_", "connected_charities_schedule__Excel_")
}

final case class Charity(
  charityItem: Int,
  charityName: String,
  charityReference: String
) extends DocumentRow

object Charity {
  implicit val format: OFormat[Charity] = Json.format[Charity]
}
