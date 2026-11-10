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

package uk.gov.hmrc.charitiesclaimsvalidation.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.FileStatus

class FileStatusSpec extends AnyWordSpec with Matchers:

  "FileStatus" should:
    "serialize and deserialize correctly" in:
      FileStatus.format.writes(FileStatus.AWAITING_UPLOAD) shouldBe JsString("AWAITING_UPLOAD")
      FileStatus.format.reads(JsString("AWAITING_UPLOAD")).get shouldBe FileStatus.AWAITING_UPLOAD

      FileStatus.format.writes(FileStatus.VERIFYING) shouldBe JsString("VERIFYING")
      FileStatus.format.reads(JsString("VERIFYING")).get shouldBe FileStatus.VERIFYING

      FileStatus.format.writes(FileStatus.VERIFICATION_FAILED) shouldBe JsString("VERIFICATION_FAILED")
      FileStatus.format.reads(JsString("VERIFICATION_FAILED")).get shouldBe FileStatus.VERIFICATION_FAILED

      FileStatus.format.writes(FileStatus.VALIDATING) shouldBe JsString("VALIDATING")
      FileStatus.format.reads(JsString("VALIDATING")).get shouldBe FileStatus.VALIDATING

      FileStatus.format.writes(FileStatus.VALIDATED) shouldBe JsString("VALIDATED")
      FileStatus.format.reads(JsString("VALIDATED")).get shouldBe FileStatus.VALIDATED

      FileStatus.format.writes(FileStatus.VALIDATION_FAILED) shouldBe JsString("VALIDATION_FAILED")
      FileStatus.format.reads(JsString("VALIDATION_FAILED")).get shouldBe FileStatus.VALIDATION_FAILED
