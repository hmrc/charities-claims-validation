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
