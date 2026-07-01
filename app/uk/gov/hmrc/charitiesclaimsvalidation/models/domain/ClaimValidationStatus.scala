/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import play.api.libs.json.OFormat
import uk.gov.hmrc.charitiesclaimsvalidation.models.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.formats.ClaimValidationStatusFormats

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
}
