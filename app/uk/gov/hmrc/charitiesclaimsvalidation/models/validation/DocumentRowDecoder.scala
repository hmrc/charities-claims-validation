/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.validation

trait DocumentRowDecoder[A <: DocumentRow]:
  def fromCells(cells: List[String]): Option[A]

object DocumentRowDecoder:
  def apply[A <: DocumentRow](using dec: DocumentRowDecoder[A]): DocumentRowDecoder[A] = dec

  given DocumentRowDecoder[OtherIncomeRow] with
    def fromCells(cells: List[String]): Option[OtherIncomeRow] =
      val itemNumber   = cells.headOption.getOrElse("").trim
      val payerName    = cells.lift(1).getOrElse("").trim
      val paymentDate  = cells.lift(2).getOrElse("").trim
      val grossPayment = cells.lift(3).getOrElse("").trim
      val taxDeducted  = cells.lift(4).getOrElse("").trim

      val nonItemFieldsEmpty =
        payerName.isEmpty &&
          paymentDate.isEmpty &&
          grossPayment.isEmpty &&
          taxDeducted.isEmpty

      if nonItemFieldsEmpty then None
      else
        Some(
          OtherIncomeRow(
            otherIncomeItem = itemNumber,
            payerName = payerName,
            paymentDate = paymentDate,
            grossPayment = grossPayment,
            taxDeducted = taxDeducted
          )
        )

  given DocumentRowDecoder[ConnectedCharitiesRow] with
    def fromCells(cells: List[String]): Option[ConnectedCharitiesRow] =
      val itemNumber       = cells.headOption.getOrElse("").trim
      val charityName      = cells.lift(1).getOrElse("").trim
      val charityReference = cells.lift(2).getOrElse("").trim

      val nonItemFieldsEmpty =
        charityName.isEmpty &&
          charityReference.isEmpty

      if nonItemFieldsEmpty then None
      else
        Some(
          ConnectedCharitiesRow(
            connectedCharitiesItem = itemNumber,
            charityName = charityName,
            charityReference = charityReference
          )
        )

  given DocumentRowDecoder[GiftAidDonationRow] with

    def fromCells(cells: List[String]): Option[GiftAidDonationRow] =
      val item                = cells.headOption.getOrElse("").trim
      val title               = cells.lift(1).getOrElse("").trim
      val firstName           = cells.lift(2).getOrElse("").trim
      val lastName            = cells.lift(3).getOrElse("").trim
      val houseNameOrNumber   = cells.lift(4).getOrElse("").trim
      val postCode            = cells.lift(5).getOrElse("").trim
      val aggregatedDonations = cells.lift(6).getOrElse("").trim
      val sponsoredEvent      = cells.lift(7).getOrElse("").trim
      val donationDate        = cells.lift(8).getOrElse("").trim
      val amount              = cells.lift(9).getOrElse("").trim

      val nonItemFieldsEmpty =
        title.isEmpty &&
          firstName.isEmpty &&
          lastName.isEmpty &&
          houseNameOrNumber.isEmpty &&
          postCode.isEmpty &&
          aggregatedDonations.isEmpty &&
          sponsoredEvent.isEmpty &&
          donationDate.isEmpty &&
          amount.isEmpty

      if nonItemFieldsEmpty then None
      else
        Some(
          GiftAidDonationRow(
            donationItem = item,
            donorTitle = title,
            donorFirstName = firstName,
            donorLastName = lastName,
            donorHouse = houseNameOrNumber,
            donorPostcode = postCode,
            aggregatedDonations = aggregatedDonations,
            sponsoredEvent = sponsoredEvent,
            donationDate = donationDate,
            donationAmount = amount
          )
        )

  given DocumentRowDecoder[CommunityBuildingRow] with
    def fromCells(cells: List[String]): Option[CommunityBuildingRow] =
      val item               = cells.headOption.getOrElse("").trim
      val buildingName       = cells.lift(1).getOrElse("").trim
      val firstLineOfAddress = cells.lift(2).getOrElse("").trim
      val postcode           = cells.lift(3).getOrElse("").trim
      val taxYear1           = cells.lift(4).getOrElse("").trim
      val amount1            = cells.lift(5).getOrElse("").trim
      val taxYear2           = cells.lift(6).getOrElse("").trim
      val amount2            = cells.lift(7).getOrElse("").trim

      val nonItemFieldsEmpty =
        buildingName.isEmpty &&
          firstLineOfAddress.isEmpty &&
          postcode.isEmpty &&
          taxYear1.isEmpty &&
          amount1.isEmpty &&
          taxYear2.isEmpty &&
          amount2.isEmpty

      if nonItemFieldsEmpty then None
      else
        Some(
          CommunityBuildingRow(
            item = item,
            buildingName = buildingName,
            firstLineOfAddress = firstLineOfAddress,
            postcode = postcode,
            taxYear1 = taxYear1,
            amount1 = amount1,
            taxYear2 = taxYear2,
            amount2 = amount2
          )
        )
