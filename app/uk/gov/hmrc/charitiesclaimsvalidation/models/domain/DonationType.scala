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
