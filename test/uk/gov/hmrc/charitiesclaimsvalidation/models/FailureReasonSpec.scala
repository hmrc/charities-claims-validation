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
