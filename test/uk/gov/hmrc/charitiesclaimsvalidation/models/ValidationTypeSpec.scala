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
