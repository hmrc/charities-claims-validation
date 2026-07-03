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
