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

package uk.gov.hmrc.charitiesclaimsvalidation.config

object Constants {
  inline val charityKeyType = "CHARID"

  inline val isoDateLength = 10

  inline val maxIntegerDigits = 20
  inline val maxMoneyDigits = 15

  inline val maxPayerNameLength = 40

  inline val maxHouseNameLength = 40
  inline val maxBuildingNameLength = 160
  inline val maxAddressLineLength = 40

  inline val maxCharityNameLength = 160

  inline val maxCommunityBuildingTaxYears = 3

  inline val maxCommunityBuildingItemLength = 500
  inline val maxConnectedCharitiesItemLength = 200
  inline val maxOtherIncomeItemLength = 200
  inline val giftAidValidationItemLength = 1000

  inline val maxFirstNameLength = 35

  inline val maxAggregateDonationsLength = 35

  inline val uploadExpiryDays      = 7

  inline val requiredParamIsEmpty  = "REQUIRED_PARAM_IS_EMPTY"
}
