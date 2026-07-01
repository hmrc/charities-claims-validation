/*
 * Copyright 2025 HM Revenue & Customs
 *
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
