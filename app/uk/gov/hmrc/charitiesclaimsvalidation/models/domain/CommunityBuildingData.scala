/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.charitiesclaimsvalidation.models.formats.JsonImplicits.bigDecimalWrites
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.DocumentRow

final case class CommunityBuildingData(
  totalOfAllAmounts: Option[BigDecimal],
  communityBuildings: List[CommunityBuilding]
)

final case class CommunityBuilding(
  communityBuildingItem: Int,
  buildingName: String,
  firstLineOfAddress: String,
  postcode: String,
  taxYear1: Int,
  amountYear1: BigDecimal,
  taxYear2: Option[Int],
  amountYear2: Option[BigDecimal]
) extends DocumentRow

object CommunityBuilding {
  implicit val format: OFormat[CommunityBuilding] = Json.format[CommunityBuilding]
}

object CommunityBuildingData {
  implicit val format: OFormat[CommunityBuildingData] = Json.format[CommunityBuildingData]

  val fileNames = List("community_buildings_schedule__Libre", "community_buildings_excel")
}
