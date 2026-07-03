/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
