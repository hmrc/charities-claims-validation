/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.ValidationType

class ValidationTypeSpec extends AnyWordSpec with Matchers:

  "ValidationType" should:
    "serialize and deserialize correctly" in:
      ValidationType.format.writes(ValidationType.GiftAid) shouldBe JsString("GiftAid")
      ValidationType.format.reads(JsString("GiftAid")).get shouldBe ValidationType.GiftAid

      ValidationType.format.writes(ValidationType.OtherIncome) shouldBe JsString("OtherIncome")
      ValidationType.format.reads(JsString("OtherIncome")).get shouldBe ValidationType.OtherIncome

      ValidationType.format.writes(ValidationType.CommunityBuildings) shouldBe JsString("CommunityBuildings")
      ValidationType.format.reads(JsString("CommunityBuildings")).get shouldBe ValidationType.CommunityBuildings

      ValidationType.format.writes(ValidationType.ConnectedCharities) shouldBe JsString("ConnectedCharities")
      ValidationType.format.reads(JsString("ConnectedCharities")).get shouldBe ValidationType.ConnectedCharities
