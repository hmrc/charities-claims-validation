/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import play.api.libs.functional.syntax.*
import play.api.libs.json.{Format, Json, OFormat, OWrites, Reads, __}
import uk.gov.hmrc.charitiesclaimsvalidation.models.formats.JsonImplicits.bigDecimalWrites
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.DocumentRow

import java.time.LocalDate

final case class OtherIncomeData(
  adjustmentForOtherIncomePreviousOverClaimed: Option[BigDecimal],
  totalOfGrossPayments: Option[BigDecimal],
  totalOfTaxDeducted: Option[BigDecimal],
  otherIncomes: List[OtherIncome]
)

final case class OtherIncome(
  otherIncomeItem: Int,
  payerName: String,
  paymentDate: LocalDate,
  grossPayment: BigDecimal,
  taxDeducted: BigDecimal
) extends DocumentRow

object OtherIncome {
  private implicit val localDateFormat: Format[LocalDate] = Format(
    Reads.localDateReads("yyyy-MM-dd"),
    play.api.libs.json.Writes.DefaultLocalDateWrites
  )

  implicit val format: OFormat[OtherIncome] = Json.format[OtherIncome]
}

object OtherIncomeData {
  implicit val reads: Reads[OtherIncomeData] = Json.reads[OtherIncomeData]

  implicit val writes: OWrites[OtherIncomeData] = (
    (__ \ "adjustmentForOtherIncomePreviousOverClaimed").write[BigDecimal].contramap[Option[BigDecimal]](_.getOrElse(BigDecimal(0))) and
      (__ \ "totalOfGrossPayments").writeNullable[BigDecimal] and
      (__ \ "totalOfTaxDeducted").writeNullable[BigDecimal] and
      (__ \ "otherIncomes").write[List[OtherIncome]]
  )(d => (d.adjustmentForOtherIncomePreviousOverClaimed, d.totalOfGrossPayments, d.totalOfTaxDeducted, d.otherIncomes))

  implicit val format: OFormat[OtherIncomeData] = OFormat(reads, writes)

  val fileNames = List("other_income_schedule_Libre", "other_income_schedule")
}
