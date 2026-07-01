/*
 * Copyright 2025 HM Revenue & Customs
 *
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
