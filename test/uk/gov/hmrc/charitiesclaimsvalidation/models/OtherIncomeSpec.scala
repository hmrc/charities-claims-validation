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
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{OtherIncome, OtherIncomeData}

import java.time.LocalDate

class OtherIncomeSpec extends AnyWordSpec with Matchers:

  private val localDate = LocalDate.now

  "OtherIncome" should:
    "serialize and deserialize OtherIncome correctly" in:
      val request = OtherIncome(
        otherIncomeItem = 1,
        payerName = "test name",
        paymentDate = localDate,
        grossPayment = 1.1,
        taxDeducted = 1.1
      )

      val json   = Json.toJson(request)(OtherIncome.format)
      val parsed = json.as[OtherIncome]

      parsed.payerName shouldBe request.payerName
      parsed.paymentDate shouldBe request.paymentDate
      parsed.grossPayment shouldBe request.grossPayment

  "OtherIncomeData" should:
    "serialize and deserialize OtherIncomeData correctly" in:
      val otherIncome = OtherIncome(
        otherIncomeItem = 1,
        payerName = "test name",
        paymentDate = localDate,
        grossPayment = 1.1,
        taxDeducted = 1.1
      )

      val request = OtherIncomeData(
        Some(1.1),
        Some(1.1),
        Some(1.1),
        List(otherIncome)
      )

      val json   = Json.toJson(request)(OtherIncomeData.format)
      val parsed = json.as[OtherIncomeData]

      parsed.otherIncomes shouldBe request.otherIncomes
      parsed.adjustmentForOtherIncomePreviousOverClaimed shouldBe request.adjustmentForOtherIncomePreviousOverClaimed
      parsed.totalOfGrossPayments shouldBe request.totalOfGrossPayments
      parsed.totalOfTaxDeducted shouldBe request.totalOfTaxDeducted
