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

enum FileStatus {
  case AWAITING_UPLOAD
  case VERIFYING
  case VERIFICATION_FAILED
  case VALIDATING
  case VALIDATED
  case VALIDATION_FAILED
}

object FileStatus {
  implicit val format: Format[FileStatus] = new Format[FileStatus] {
    override def writes(status: FileStatus): JsValue = JsString(status.toString)

    override def reads(json: JsValue): JsResult[FileStatus] = json match {
      case JsString(str) if str == AWAITING_UPLOAD.toString     => JsSuccess(AWAITING_UPLOAD)
      case JsString(str) if str == VERIFYING.toString           => JsSuccess(VERIFYING)
      case JsString(str) if str == VERIFICATION_FAILED.toString => JsSuccess(VERIFICATION_FAILED)
      case JsString(str) if str == VALIDATING.toString          => JsSuccess(VALIDATING)
      case JsString(str) if str == VALIDATED.toString           => JsSuccess(VALIDATED)
      case JsString(str) if str == VALIDATION_FAILED.toString   => JsSuccess(VALIDATION_FAILED)
      case _                                                    => JsError("Invalid FileStatus")
    }
  }
}
