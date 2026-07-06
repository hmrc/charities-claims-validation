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

import play.api.libs.json.OFormat
import uk.gov.hmrc.charitiesclaimsvalidation.models.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.formats.ClaimValidationStatusFormats
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.Instant

sealed trait ClaimValidationStatus {
  def claimId: String
  def reference: String
  def validationType: ValidationType
  def fileStatus: String
  def createdAt: Instant
  def updatedAt: Instant
}

final case class AwaitingUploadStatus(
  claimId: String,
  reference: String,
  validationType: ValidationType,
  uploadUrl: String,
  initiateTimestamp: Instant,
  fields: Option[Map[String, String]],
  createdAt: Instant,
  updatedAt: Instant
) extends ClaimValidationStatus {
  override def fileStatus: String = "AWAITING_UPLOAD"
}

final case class VerifyingStatus(
  claimId: String,
  reference: String,
  validationType: ValidationType,
  createdAt: Instant,
  updatedAt: Instant
) extends ClaimValidationStatus {
  override def fileStatus: String = "VERIFYING"
}

final case class VerificationFailedStatus(
  claimId: String,
  reference: String,
  validationType: ValidationType,
  failureDetails: FailureDetails,
  createdAt: Instant,
  updatedAt: Instant
) extends ClaimValidationStatus {
  override def fileStatus: String = "VERIFICATION_FAILED"
}

final case class ValidatingStatus(
  claimId: String,
  reference: String,
  validationType: ValidationType,
  createdAt: Instant,
  updatedAt: Instant
) extends ClaimValidationStatus {
  override def fileStatus: String = "VALIDATING"
}

final case class ValidatedStatus(
  claimId: String,
  reference: String,
  validationType: ValidationType,
  giftAidScheduleData: Option[GiftAidScheduleData] = None,
  otherIncomeData: Option[OtherIncomeData] = None,
  communityBuildingsData: Option[CommunityBuildingData] = None,
  connectedCharitiesData: Option[ConnectedCharitiesData] = None,
  createdAt: Instant,
  updatedAt: Instant
) extends ClaimValidationStatus {
  override def fileStatus: String = "VALIDATED"
}

final case class ValidationFailedStatus(
  claimId: String,
  reference: String,
  validationType: ValidationType,
  giftAidScheduleData: Option[GiftAidScheduleData] = None,
  otherIncomeData: Option[OtherIncomeData] = None,
  communityBuildingsData: Option[CommunityBuildingData] = None,
  connectedCharitiesData: Option[ConnectedCharitiesData] = None,
  errors: Seq[ValidationError],
  createdAt: Instant,
  updatedAt: Instant
) extends ClaimValidationStatus {
  override def fileStatus: String = "VALIDATION_FAILED"
}

object ClaimValidationStatus {
  implicit val format: OFormat[ClaimValidationStatus] = ClaimValidationStatusFormats.claimValidationStatusFormat

  def encryptedFormat(using Encrypter with Decrypter): OFormat[ClaimValidationStatus] =
    ClaimValidationStatusFormats.encryptedClaimValidationStatusFormat
}
