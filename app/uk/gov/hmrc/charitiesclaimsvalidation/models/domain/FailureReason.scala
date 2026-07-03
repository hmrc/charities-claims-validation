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

sealed trait FailureReason

object FailureReason {
  case object Quarantine extends FailureReason

  case object Rejected extends FailureReason

  case object Unknown extends FailureReason

  implicit val format: Format[FailureReason] = new Format[FailureReason] {
    override def writes(reason: FailureReason): JsValue = reason match {
      case Quarantine => JsString("QUARANTINE")
      case Rejected   => JsString("REJECTED")
      case Unknown    => JsString("UNKNOWN")
    }

    override def reads(json: JsValue): JsResult[FailureReason] = json match {
      case JsString("QUARANTINE") => JsSuccess(Quarantine)
      case JsString("REJECTED")   => JsSuccess(Rejected)
      case JsString("UNKNOWN")    => JsSuccess(Unknown)
      case _                      => JsError("Invalid FailureReason")
    }
  }
}
