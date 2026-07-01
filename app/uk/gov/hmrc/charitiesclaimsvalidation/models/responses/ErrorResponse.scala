/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.responses

import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.FileStatus

sealed trait ErrorResponse {
  val error: String
  val message: String
  val success: Option[Boolean] = None
}

trait ClaimError extends ErrorResponse {
  def claimId: String
  def referenceId: String
}

final case class NotFoundClaimReference(claimId: String, referenceId: String) extends ClaimError {
  val error            = "CLAIM_REFERENCE_DOES_NOT_EXIST"
  val message          = s"There is no reference=$referenceId found for the given claimId=$claimId"
  override val success = Some(false)
}

final case class InvalidUpdateFileStatusRequest(claimId: String, referenceId: String, details: String) extends ClaimError {
  val error            = "INVALID_UPDATE_FILE_STATUS_REQUEST"
  val message          = s"Invalid update file status request for claimId=$claimId, reference=$referenceId: $details"
  override val success = Some(false)
}

final case class InvalidFileStatus(claimId: String, referenceId: String, status: FileStatus) extends ClaimError {
  val error            = "INVALID_FILE_STATUS"
  val message          = s"Invalid file status '$status' in update file status request for claimId=$claimId, reference=$referenceId"
  override val success = Some(false)
}

final case class UpscanCallbackUpdateFailed(claimId: String, referenceId: String) extends ClaimError {
  val error   = "UPSCAN_CALLBACK_UPDATE_FAILED"
  val message = s"Race condition: document existed during find but not during update for claimId=$claimId, reference=$referenceId"
}

final case class UpscanCallbackFailed(claimId: String, referenceId: String) extends ClaimError {
  val error   = "UPSCAN_CALLBACK_PROCESSING_FAILED"
  val message = s"Error processing upscan callback for claimId=$claimId, reference=$referenceId"
}

final case class InvalidUploadRequest(claimId: String, details: String) extends ErrorResponse {
  val error            = "INVALID_UPLOAD_REQUEST"
  val message          = s"Invalid upload tracking request for claimId=$claimId: $details"
  override val success = Some(false)
}

final case class InvalidUpscanCallbackRequest(claimId: String, details: String) extends ErrorResponse {
  val error   = "INVALID_UPSCAN_CALLBACK_REQUEST"
  val message = s"Invalid callback request for claimId=$claimId: $details"
}

final case class ExpiredClaimReference(claimId: String, referenceId: String) extends ClaimError {
  val error   = "CLAIM_REFERENCE_HAS_EXPIRED"
  val message = s"The requested reference=$referenceId for claim claimId=$claimId has expired"
}

final case class ClaimDoesNotExist(claimId: String) extends ErrorResponse {
  val error   = "CLAIM_DOES_NOT_EXIST"
  val message = s"There is no claim found for the given claimId=$claimId"
}

final case class InternalServiceError(details: String) extends ErrorResponse {
  val error   = "INTERNAL_SERVER_ERROR"
  val message = "Currently experiencing issues with our service"
}

final case class EmptyRequiredParam(param: String) extends ErrorResponse {
  val error   = "REQUIRED_PARAM_IS_EMPTY"
  val message = s"Missing $param in the request"
}
