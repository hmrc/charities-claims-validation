/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.GiftAidDonationRow
import uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation.CommonFileValidation.removeNonWesternCharacters

enum DonationType(val isAggregated: Boolean):
  case Aggregated    extends DonationType(true)
  case NonAggregated extends DonationType(false)

object DonationType:
  def fromRow(row: GiftAidDonationRow): DonationType =
    if (removeNonWesternCharacters(row.aggregatedDonations).trim.nonEmpty)
      DonationType.Aggregated
    else
      DonationType.NonAggregated
