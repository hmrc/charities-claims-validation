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

import play.api.libs.json.*

sealed trait ValidationType

object ValidationType {
  case object GiftAid extends ValidationType

  case object OtherIncome extends ValidationType

  case object CommunityBuildings extends ValidationType

  case object ConnectedCharities extends ValidationType

  implicit val format: Format[ValidationType] = new Format[ValidationType] {
    override def writes(validationType: ValidationType): JsValue = validationType match {
      case GiftAid            => JsString("GiftAid")
      case OtherIncome        => JsString("OtherIncome")
      case CommunityBuildings => JsString("CommunityBuildings")
      case ConnectedCharities => JsString("ConnectedCharities")
    }

    override def reads(json: JsValue): JsResult[ValidationType] = json match {
      case JsString("GiftAid")            => JsSuccess(GiftAid)
      case JsString("OtherIncome")        => JsSuccess(OtherIncome)
      case JsString("CommunityBuildings") => JsSuccess(CommunityBuildings)
      case JsString("ConnectedCharities") => JsSuccess(ConnectedCharities)
      case _                              => JsError("Invalid ValidationType")
    }
  }
}
