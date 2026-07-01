/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain

import play.api.libs.json.*

case class FileStatusUpdateRequest(fileStatus: FileStatus)

object FileStatusUpdateRequest {
  implicit val format: OFormat[FileStatusUpdateRequest] = Json.format[FileStatusUpdateRequest]
}
