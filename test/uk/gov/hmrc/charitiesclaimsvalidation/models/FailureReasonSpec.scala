/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.FailureReason

class FailureReasonSpec extends AnyWordSpec with Matchers:

  "FailureReason" should:
    "serialize and deserialize correctly" in:
      FailureReason.format.writes(FailureReason.Quarantine) shouldBe JsString("QUARANTINE")
      FailureReason.format.reads(JsString("QUARANTINE")).get shouldBe FailureReason.Quarantine

      FailureReason.format.writes(FailureReason.Rejected) shouldBe JsString("REJECTED")
      FailureReason.format.reads(JsString("REJECTED")).get shouldBe FailureReason.Rejected

      FailureReason.format.writes(FailureReason.Unknown) shouldBe JsString("UNKNOWN")
      FailureReason.format.reads(JsString("UNKNOWN")).get shouldBe FailureReason.Unknown
