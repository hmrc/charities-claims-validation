/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.generators

import eu.timepit.refined.types.all.NonEmptyString
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.ValidationType
import uk.gov.hmrc.charitiesclaimsvalidation.models.requests
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.ValidationType.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.requests.UploadRequest

import java.net.URI
import java.time.Instant

trait ModelGenerators {

  given arbitraryValidationType: Arbitrary[ValidationType] =
    Arbitrary(Gen.oneOf(GiftAid, OtherIncome, CommunityBuildings, ConnectedCharities))

  given arbitraryUploadRequest: Arbitrary[UploadRequest] = Arbitrary {
    for {
      reference      <- Gen.oneOf(NonEmptyString.unsafeFrom("3424324324324"), NonEmptyString.unsafeFrom("54254542545"))
      validationType <- arbitrary[ValidationType]
      uploadUri         = URI.create("http://test-url.com")
      initiateTimestamp = Instant.now
      fields = Some(
        Map(
          "acl"                     -> "private",
          "key"                     -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
          "policy"                  -> "xxxxxxxx==",
          "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
          "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
          "x-amz-date"              -> "yyyyMMddThhmmssZ",
          "x-amz-meta-callback-url" -> "https->//myservice.com/callback",
          "x-amz-signature"         -> "xxxx",
          "success_action_redirect" -> "https->//myservice.com/nextPage",
          "error_action_redirect"   -> "https->//myservice.com/errorPage"
        )
      )
    } yield requests.UploadRequest(
      reference,
      validationType,
      uploadUri,
      initiateTimestamp,
      fields
    )
  }

}
