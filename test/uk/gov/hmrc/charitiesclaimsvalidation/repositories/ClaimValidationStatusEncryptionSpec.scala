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

package uk.gov.hmrc.charitiesclaimsvalidation.repositories

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito.*
import uk.gov.hmrc.charitiesclaimsvalidation.config.AppConfig
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}
import uk.gov.hmrc.mongo.test.MongoSupport

import java.time.{Instant, LocalDate}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class ClaimValidationStatusEncryptionSpec
    extends AnyWordSpec
    with Matchers
    with OptionValues
    with BeforeAndAfterEach
    with MongoSupport
    with MockitoSugar:

  private val aesKey                           = "ci+wy7C6jftPw6tMdjnV60T+bJOgwDXEHmYk4XWKbsM="
  private val crypto: Encrypter with Decrypter = SymmetricCryptoFactory.aesGcmCrypto(aesKey)

  private val mockConfig = mock[AppConfig]
  when(mockConfig.replaceIndexes).thenReturn(true)
  when(mockConfig.ttl).thenReturn(2592000)
  when(mockConfig.isTtlEnabled).thenReturn(true)
  when(mockConfig.retryMaxAttempts).thenReturn(3)
  when(mockConfig.retryInitialDelay).thenReturn(50.millis)
  when(mockConfig.retryMaxDelay).thenReturn(500.millis)

  private val repository = new ClaimValidationStatusRepository(mongoComponent, mockConfig, crypto)

  private val claimId   = "claim-enc-1"
  private val reference = "ref-enc-1"
  private val timestamp = Instant.parse("2025-01-01T12:00:00Z")

  private val donorLastName = "Wonderland"
  private val donorPostcode = "AB1 2CD"

  private val validatedStatus = ValidatedStatus(
    claimId = claimId,
    reference = reference,
    validationType = ValidationType.GiftAid,
    giftAidScheduleData = Some(
      GiftAidScheduleData(
        earliestDonationDate = Some(LocalDate.parse("2024-04-06")),
        prevOverclaimedGiftAid = None,
        totalDonations = Some(BigDecimal(100)),
        donations = List(
          GiftAidDonation(
            donationItem = 1,
            donorTitle = Some("Ms"),
            donorFirstName = Some("Alice"),
            donorLastName = Some(donorLastName),
            donorHouse = Some("221B"),
            donorPostcode = Some(donorPostcode),
            aggregatedDonations = None,
            sponsoredEvent = false,
            donationDate = LocalDate.parse("2024-05-01"),
            donationAmount = BigDecimal(100)
          )
        )
      )
    ),
    createdAt = timestamp,
    updatedAt = timestamp
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropDatabase()
    await(repository.ensureIndexes())
  }

  private def await[T](future: Future[T]): T = Await.result(future, 5.seconds)

  private def rawDocumentJson: String =
    await(
      mongoComponent.database
        .getCollection("claim-validation-status")
        .find()
        .headOption()
    ).value.toJson()

  "ClaimValidationStatusRepository with encryption" should {

    "store the gift aid schedule payload as ciphertext (no donor PII in plaintext at rest)" in {
      Await.result(repository.insert(validatedStatus), 5.seconds)

      val raw = rawDocumentJson

      raw should include(claimId)
      raw should include("VALIDATED")
      raw shouldNot include(donorLastName)
      raw shouldNot include(donorPostcode)
      raw shouldNot include("Alice")
      raw shouldNot include("221B")
    }

    "decrypt the payload back to the original donor details on read" in {
      Await.result(repository.insert(validatedStatus), 5.seconds)

      val readBack = Await.result(repository.findByReference(claimId, reference), 5.seconds).value

      readBack shouldBe a[ValidatedStatus]
      readBack.claimId shouldBe claimId
      readBack.reference shouldBe reference

      val donation = readBack.asInstanceOf[ValidatedStatus].giftAidScheduleData.value.donations.head
      donation.donorTitle.value shouldBe "Ms"
      donation.donorFirstName.value shouldBe "Alice"
      donation.donorLastName.value shouldBe donorLastName
      donation.donorHouse.value shouldBe "221B"
      donation.donorPostcode.value shouldBe donorPostcode
    }
  }
