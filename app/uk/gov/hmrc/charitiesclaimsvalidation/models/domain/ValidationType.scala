/*
 * Copyright 2025 HM Revenue & Customs
 *
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
