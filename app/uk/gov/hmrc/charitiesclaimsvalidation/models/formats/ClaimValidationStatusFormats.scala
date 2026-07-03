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

package uk.gov.hmrc.charitiesclaimsvalidation.models.formats

import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.{AwaitingUploadStatus, ClaimValidationStatus, CommunityBuildingData, ConnectedCharitiesData, FailureDetails, GiftAidScheduleData, OtherIncomeData, ValidatedStatus, ValidatingStatus, ValidationError, ValidationFailedStatus, ValidationType, VerificationFailedStatus, VerifyingStatus}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

object ClaimValidationStatusFormats {

  private implicit val mongoInstantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  private def awaitingUploadFormat: OFormat[AwaitingUploadStatus] = (
    (__ \ "_id" \ "claimId").format[String] and
      (__ \ "_id" \ "reference").format[String] and
      (__ \ "validationType").format[ValidationType] and
      (__ \ "uploadUrl").format[String] and
      (__ \ "initiateTimestamp").format[Instant] and
      (__ \ "fields").formatNullable[Map[String, String]] and
      (__ \ "createdAt").format[Instant] and
      (__ \ "updatedAt").format[Instant]
  )(
    AwaitingUploadStatus.apply,
    (s: AwaitingUploadStatus) => (s.claimId, s.reference, s.validationType, s.uploadUrl, s.initiateTimestamp, s.fields, s.createdAt, s.updatedAt)
  )

  private def verifyingFormat: OFormat[VerifyingStatus] = (
    (__ \ "_id" \ "claimId").format[String] and
      (__ \ "_id" \ "reference").format[String] and
      (__ \ "validationType").format[ValidationType] and
      (__ \ "createdAt").format[Instant] and
      (__ \ "updatedAt").format[Instant]
  )(VerifyingStatus.apply, (s: VerifyingStatus) => (s.claimId, s.reference, s.validationType, s.createdAt, s.updatedAt))

  private def verificationFailedFormat: OFormat[VerificationFailedStatus] = (
    (__ \ "_id" \ "claimId").format[String] and
      (__ \ "_id" \ "reference").format[String] and
      (__ \ "validationType").format[ValidationType] and
      (__ \ "failureDetails").format[FailureDetails] and
      (__ \ "createdAt").format[Instant] and
      (__ \ "updatedAt").format[Instant]
  )(
    VerificationFailedStatus.apply,
    (s: VerificationFailedStatus) => (s.claimId, s.reference, s.validationType, s.failureDetails, s.createdAt, s.updatedAt)
  )

  private def validatingFormat: OFormat[ValidatingStatus] = (
    (__ \ "_id" \ "claimId").format[String] and
      (__ \ "_id" \ "reference").format[String] and
      (__ \ "validationType").format[ValidationType] and
      (__ \ "createdAt").format[Instant] and
      (__ \ "updatedAt").format[Instant]
  )(ValidatingStatus.apply, (s: ValidatingStatus) => (s.claimId, s.reference, s.validationType, s.createdAt, s.updatedAt))

  private def validatedFormat: OFormat[ValidatedStatus] = (
    (__ \ "_id" \ "claimId").format[String] and
      (__ \ "_id" \ "reference").format[String] and
      (__ \ "validationType").format[ValidationType] and
      (__ \ "giftAidScheduleData").formatNullable[GiftAidScheduleData] and
      (__ \ "otherIncomeData").formatNullable[OtherIncomeData] and
      (__ \ "communityBuildingsData").formatNullable[CommunityBuildingData] and
      (__ \ "connectedCharitiesData").formatNullable[ConnectedCharitiesData] and
      (__ \ "createdAt").format[Instant] and
      (__ \ "updatedAt").format[Instant]
  )(
    ValidatedStatus.apply,
    (s: ValidatedStatus) =>
      (
        s.claimId,
        s.reference,
        s.validationType,
        s.giftAidScheduleData,
        s.otherIncomeData,
        s.communityBuildingsData,
        s.connectedCharitiesData,
        s.createdAt,
        s.updatedAt
      )
  )

  private def validationFailedFormat: OFormat[ValidationFailedStatus] = (
    (__ \ "_id" \ "claimId").format[String] and
      (__ \ "_id" \ "reference").format[String] and
      (__ \ "validationType").format[ValidationType] and
      (__ \ "giftAidScheduleData").formatNullable[GiftAidScheduleData] and
      (__ \ "otherIncomeData").formatNullable[OtherIncomeData] and
      (__ \ "communityBuildingsData").formatNullable[CommunityBuildingData] and
      (__ \ "connectedCharitiesData").formatNullable[ConnectedCharitiesData] and
      (__ \ "errors").format[Seq[ValidationError]] and
      (__ \ "createdAt").format[Instant] and
      (__ \ "updatedAt").format[Instant]
  )(
    ValidationFailedStatus.apply,
    (s: ValidationFailedStatus) =>
      (
        s.claimId,
        s.reference,
        s.validationType,
        s.giftAidScheduleData,
        s.otherIncomeData,
        s.communityBuildingsData,
        s.connectedCharitiesData,
        s.errors,
        s.createdAt,
        s.updatedAt
      )
  )

  implicit val claimValidationStatusFormat: OFormat[ClaimValidationStatus] = new OFormat[ClaimValidationStatus] {
    override def writes(status: ClaimValidationStatus): JsObject = {
      val baseJson = status match {
        case s: AwaitingUploadStatus     => awaitingUploadFormat.writes(s)
        case s: VerifyingStatus          => verifyingFormat.writes(s)
        case s: VerificationFailedStatus => verificationFailedFormat.writes(s)
        case s: ValidatingStatus         => validatingFormat.writes(s)
        case s: ValidatedStatus          => validatedFormat.writes(s)
        case s: ValidationFailedStatus   => validationFailedFormat.writes(s)
      }
      baseJson + ("fileStatus" -> JsString(status.fileStatus))
    }

    override def reads(json: JsValue): JsResult[ClaimValidationStatus] = {
      (json \ "fileStatus").validate[String].flatMap {
        case "AWAITING_UPLOAD"     => awaitingUploadFormat.reads(json)
        case "VERIFYING"           => verifyingFormat.reads(json)
        case "VERIFICATION_FAILED" => verificationFailedFormat.reads(json)
        case "VALIDATING"          => validatingFormat.reads(json)
        case "VALIDATED"           => validatedFormat.reads(json)
        case "VALIDATION_FAILED"   => validationFailedFormat.reads(json)
        case other                 => JsError(s"Unknown fileStatus: $other")
      }
    }
  }
}
