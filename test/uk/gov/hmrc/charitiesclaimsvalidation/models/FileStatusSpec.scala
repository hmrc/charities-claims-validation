/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.FileStatus

class FileStatusSpec extends AnyWordSpec with Matchers:

  "FileStatus" should:
    "serialize and deserialize correctly" in:
      FileStatus.format.writes(FileStatus.AwaitingUpload) shouldBe JsString("AWAITING_UPLOAD")
      FileStatus.format.reads(JsString("AWAITING_UPLOAD")).get shouldBe FileStatus.AwaitingUpload

      FileStatus.format.writes(FileStatus.Verifying) shouldBe JsString("VERIFYING")
      FileStatus.format.reads(JsString("VERIFYING")).get shouldBe FileStatus.Verifying

      FileStatus.format.writes(FileStatus.VerificationFailed) shouldBe JsString("VERIFICATION_FAILED")
      FileStatus.format.reads(JsString("VERIFICATION_FAILED")).get shouldBe FileStatus.VerificationFailed

      FileStatus.format.writes(FileStatus.Validating) shouldBe JsString("VALIDATING")
      FileStatus.format.reads(JsString("VALIDATING")).get shouldBe FileStatus.Validating

      FileStatus.format.writes(FileStatus.Validated) shouldBe JsString("VALIDATED")
      FileStatus.format.reads(JsString("VALIDATED")).get shouldBe FileStatus.Validated

      FileStatus.format.writes(FileStatus.ValidationFailed) shouldBe JsString("VALIDATION_FAILED")
      FileStatus.format.reads(JsString("VALIDATION_FAILED")).get shouldBe FileStatus.ValidationFailed
