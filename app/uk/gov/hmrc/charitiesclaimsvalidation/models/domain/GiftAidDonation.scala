/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import play.api.libs.json.{Format, Json, OFormat, Reads}
import uk.gov.hmrc.charitiesclaimsvalidation.models.formats.JsonImplicits.bigDecimalWrites
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.DocumentRow

import java.time.LocalDate

final case class GiftAidDonation(
  donationItem: Int,
  donorTitle: Option[String],
  donorFirstName: Option[String],
  donorLastName: Option[String],
  donorHouse: Option[String],
  donorPostcode: Option[String],
  aggregatedDonations: Option[String],
  sponsoredEvent: Boolean,
  donationDate: LocalDate,
  donationAmount: BigDecimal
) extends DocumentRow

object GiftAidDonation {

  private implicit val localDateFormat: Format[LocalDate] = Format(
    Reads.localDateReads("yyyy-MM-dd"),
    play.api.libs.json.Writes.DefaultLocalDateWrites
  )

  implicit val format: OFormat[GiftAidDonation] = Json.format[GiftAidDonation]
}
