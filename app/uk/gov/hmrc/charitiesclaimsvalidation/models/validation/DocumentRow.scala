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

package uk.gov.hmrc.charitiesclaimsvalidation.models.validation

import play.api.libs.json.{Json, OFormat}

trait DocumentRow

final case class OtherIncomeRow(
  otherIncomeItem: String,
  payerName: String,
  paymentDate: String,
  grossPayment: String,
  taxDeducted: String
) extends DocumentRow

object OtherIncomeRow {
  implicit val format: OFormat[OtherIncomeRow] = Json.format[OtherIncomeRow]

  val ADJ_OTHER_INCOME_PREV_CLAIM_FIELD: SheetCell = SheetCell(13, 2)

  def layout: SheetLayout = SheetLayout(
    rowRange = 24 until 225,
    cellRange = 1 until 7
  )
}

final case class ConnectedCharitiesRow(
  connectedCharitiesItem: String,
  charityName: String,
  charityReference: String
) extends DocumentRow

object ConnectedCharitiesRow {
  implicit val format: OFormat[ConnectedCharitiesRow] = Json.format[ConnectedCharitiesRow]

  def layout: SheetLayout = SheetLayout(
    rowRange = 15 until 216,
    cellRange = 1 until 5
  )
}

final case class GiftAidDonationRow(
  donationItem: String,
  donorTitle: String,
  donorFirstName: String,
  donorLastName: String,
  donorHouse: String,
  donorPostcode: String,
  aggregatedDonations: String,
  sponsoredEvent: String,
  donationDate: String,
  donationAmount: String
) extends DocumentRow

object GiftAidDonationRow {
  implicit val format: OFormat[GiftAidDonationRow] = Json.format[GiftAidDonationRow]

  val EARLIEST_DONATION_DATE_FIELD: SheetCell = SheetCell(12, 3)

  val PREVIOUSLY_OVERCLAIMED_AMOUNT_FIELD: SheetCell = SheetCell(16, 3)

  def layout: SheetLayout = SheetLayout(
    rowRange = 24 until 1025,
    cellRange = 1 until 12
  )
}

final case class CommunityBuildingRow(
  item: String,
  buildingName: String,
  firstLineOfAddress: String,
  postcode: String,
  taxYear1: String,
  amount1: String,
  taxYear2: String,
  amount2: String
) extends DocumentRow

object CommunityBuildingRow {
  implicit val format: OFormat[CommunityBuildingRow] = Json.format[CommunityBuildingRow]

  def layout: SheetLayout = SheetLayout(
    rowRange = 17 until 518,
    cellRange = 1 until 10
  )
}

final case class SheetLayout(rowRange: Range, cellRange: Range)
final case class SheetCell(rowIndex: Int, cellIndex: Int)
