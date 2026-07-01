/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.responses

import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*

final case class UploadResultResponse(
  reference: String,
  validationType: ValidationType,
  fileStatus: String,
  uploadUrl: Option[String] = None,
  failureDetails: Option[FailureDetails] = None,
  giftAidScheduleData: Option[GiftAidScheduleData] = None,
  otherIncomeData: Option[OtherIncomeData] = None,
  communityBuildingsData: Option[CommunityBuildingData] = None,
  connectedCharitiesData: Option[ConnectedCharitiesData] = None,
  errors: Option[Seq[ValidationError]] = None
)

object UploadResultResponse {

  given writes: OWrites[UploadResultResponse] = Json.writes[UploadResultResponse]

  def fromClaimValidationStatus(status: ClaimValidationStatus): UploadResultResponse = status match {
    case s: AwaitingUploadStatus =>
      UploadResultResponse(
        reference = s.reference,
        validationType = s.validationType,
        fileStatus = s.fileStatus,
        uploadUrl = Some(s.uploadUrl)
      )

    case s: VerifyingStatus =>
      UploadResultResponse(
        reference = s.reference,
        validationType = s.validationType,
        fileStatus = s.fileStatus
      )

    case s: VerificationFailedStatus =>
      UploadResultResponse(
        reference = s.reference,
        validationType = s.validationType,
        fileStatus = s.fileStatus,
        failureDetails = Some(s.failureDetails)
      )

    case s: ValidatingStatus =>
      UploadResultResponse(
        reference = s.reference,
        validationType = s.validationType,
        fileStatus = s.fileStatus
      )

    case s: ValidatedStatus =>
      UploadResultResponse(
        reference = s.reference,
        validationType = s.validationType,
        fileStatus = s.fileStatus,
        giftAidScheduleData = s.giftAidScheduleData,
        otherIncomeData = s.otherIncomeData,
        communityBuildingsData = s.communityBuildingsData,
        connectedCharitiesData = s.connectedCharitiesData
      )

    case s: ValidationFailedStatus =>
      UploadResultResponse(
        reference = s.reference,
        validationType = s.validationType,
        fileStatus = s.fileStatus,
        giftAidScheduleData = s.giftAidScheduleData,
        otherIncomeData = s.otherIncomeData,
        communityBuildingsData = s.communityBuildingsData,
        connectedCharitiesData = s.connectedCharitiesData,
        errors = Some(s.errors)
      )
  }
}
