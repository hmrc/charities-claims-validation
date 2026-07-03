/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.DonationType
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.GiftAidDonationRow

class DonationTypeSpec extends AnyWordSpec with Matchers {

  "DonationType.fromRow" should {

    "return Aggregated when aggregatedDonations contains non-empty western characters" in {
      val row = GiftAidDonationRow("1", "", "", "", "", "", "one off", "", "24/06/15", "240.00")

      DonationType.fromRow(row) mustBe DonationType.Aggregated
      DonationType.fromRow(row).isAggregated mustBe true
    }

    "return NonAggregated when aggregatedDonations is empty" in {
      val row = GiftAidDonationRow("1", "Prof", "Henry", "House Martin", "152A", "M99 2QD", "", "", "24/06/15", "240.00")

      DonationType.fromRow(row) mustBe DonationType.NonAggregated
      DonationType.fromRow(row).isAggregated mustBe false
    }

    "return NonAggregated when aggregatedDonations contains only whitespace" in {
      val row = GiftAidDonationRow("1", "", "", "", "", "", " ", "", "24/06/15", "240.00")

      DonationType.fromRow(row) mustBe DonationType.NonAggregated
    }

    "return NonAggregated when aggregatedDonations contains only non-western characters" in {
      val row = GiftAidDonationRow("1", "", "", "", "", "", "汉字テスト", "", "24/06/15", "240.00")

      DonationType.fromRow(row) mustBe DonationType.NonAggregated
    }

    "return Aggregated when aggregatedDonations contains western characters mixed with non-western characters" in {
      val row = GiftAidDonationRow("1", "", "", "", "", "", "Donation汉字", "", "24/06/15", "240.00")

      DonationType.fromRow(row) mustBe DonationType.Aggregated
    }
  }
}
