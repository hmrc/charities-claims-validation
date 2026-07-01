/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.formats.JsonImplicits.bigDecimalWrites

import java.time.LocalDate

case class GiftAidScheduleData(
  earliestDonationDate: Option[LocalDate],
  prevOverclaimedGiftAid: Option[BigDecimal],
  totalDonations: Option[BigDecimal],
  donations: List[GiftAidDonation]
)

object GiftAidScheduleData {
  implicit val localDateFormat: Format[LocalDate] = Format(
    Reads.localDateReads("yyyy-MM-dd"),
    Writes.DefaultLocalDateWrites
  )

  implicit val reads: Reads[GiftAidScheduleData] = Json.reads[GiftAidScheduleData]

  implicit val writes: OWrites[GiftAidScheduleData] = (
    (__ \ "earliestDonationDate").writeNullable[LocalDate] and
      (__ \ "prevOverclaimedGiftAid").write[BigDecimal].contramap[Option[BigDecimal]](_.getOrElse(BigDecimal(0))) and
      (__ \ "totalDonations").writeNullable[BigDecimal] and
      (__ \ "donations").write[List[GiftAidDonation]]
  )(d => (d.earliestDonationDate, d.prevOverclaimedGiftAid, d.totalDonations, d.donations))

  implicit val format: OFormat[GiftAidScheduleData] = OFormat(reads, writes)

  val fileNames = List("gift_aid_schedule__libre_", "Gift-Aid-Schedule-Excel")
}
