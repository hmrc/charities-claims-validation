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

import play.api.libs.json.*

enum FileStatus(val status: String) {
  case AWAITING_UPLOAD     extends FileStatus("AWAITING_UPLOAD")
  case VERIFYING           extends FileStatus("VERIFYING")
  case VERIFICATION_FAILED extends FileStatus("VERIFICATION_FAILED")
  case VALIDATING          extends FileStatus("VALIDATING")
  case VALIDATED           extends FileStatus("VALIDATED")
  case VALIDATION_FAILED   extends FileStatus("VALIDATION_FAILED")
}

object FileStatus {
  implicit val format: Format[FileStatus] = new Format[FileStatus] {
    override def writes(status: FileStatus): JsValue = JsString(status.status)

    override def reads(json: JsValue): JsResult[FileStatus] = json match {
      case JsString(AWAITING_UPLOAD.status)     => JsSuccess(AWAITING_UPLOAD)
      case JsString(VERIFYING.status)           => JsSuccess(VERIFYING)
      case JsString(VERIFICATION_FAILED.status) => JsSuccess(VERIFICATION_FAILED)
      case JsString(VALIDATING.status)          => JsSuccess(VALIDATING)
      case JsString(VALIDATED.status)           => JsSuccess(VALIDATED)
      case JsString(VALIDATION_FAILED.status)   => JsSuccess(VALIDATION_FAILED)
      case _                                    => JsError("Invalid FileStatus")
    }
  }
}
