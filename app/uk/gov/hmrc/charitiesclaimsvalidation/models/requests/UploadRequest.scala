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

package uk.gov.hmrc.charitiesclaimsvalidation.models.requests

import eu.timepit.refined.types.all.NonEmptyString
import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.ValidationType
import uk.gov.hmrc.charitiesclaimsvalidation.models.formats.JsonImplicits.{*, given}

import java.time.Instant
import java.net.URI

case class UploadRequest(
  reference: NonEmptyString,
  validationType: ValidationType,
  uploadUrl: URI,
  initiateTimestamp: Instant,
  fields: Option[Map[String, String]]
)

object UploadRequest:

  val reads: Reads[UploadRequest] = {

    import play.api.libs.functional.syntax.*

    (
      (__ \ "reference").requiredNonEmpty and
        (__ \ "validationType").read[ValidationType] and
        (__ \ "uploadUrl").read[URI].filter(_.toString.nonEmpty) and
        (__ \ "initiateTimestamp").read[Instant] and
        (__ \ "fields").readNullable[Map[String, String]]
    )(UploadRequest.apply _)
  }

  val writes: OWrites[UploadRequest] = {

    import play.api.libs.functional.syntax.*

    (
      (__ \ "reference").write[NonEmptyString] and
        (__ \ "validationType").write[ValidationType] and
        (__ \ "uploadUrl").write[URI] and
        (__ \ "initiateTimestamp").write[Instant] and
        (__ \ "fields").writeNullable[Map[String, String]]
    )(uploadRequest =>
      (uploadRequest.reference, uploadRequest.validationType, uploadRequest.uploadUrl, uploadRequest.initiateTimestamp, uploadRequest.fields)
    )
  }

  given format: OFormat[UploadRequest] = OFormat(reads, writes)
