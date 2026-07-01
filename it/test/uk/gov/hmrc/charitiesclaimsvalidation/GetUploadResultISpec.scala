/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation

import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.BsonDocument
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.shouldBe
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.ApplicationLifecycle
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.charitiesclaimsvalidation.controllers.routes
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.charitiesclaimsvalidation.helpers.wiremock.WireMockServerHandler
import uk.gov.hmrc.charitiesclaimsvalidation.helpers.AuthStubs
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.ValidationType.GiftAid
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.*
import uk.gov.hmrc.charitiesclaimsvalidation.repositories.ClaimValidationStatusRepository
import uk.gov.hmrc.mongo.MongoComponent

import java.net.URI
import java.time.Instant
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration.Inf

class GetUploadResultISpec
    extends AnyFunSuite
    with GuiceOneServerPerSuite
    with WireMockServerHandler
    with AuthStubs
    with OptionValues
    with BeforeAndAfterEach {

  private val claimValidationRepository = app.injector.instanceOf[ClaimValidationStatusRepository]

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure("microservice.services.auth.port" -> wiremockPort)
    .build()

  override def beforeEach(): Unit =
    super.beforeEach()
    val mongoComponent = app.injector.instanceOf[MongoComponent]

    Await.ready(
      Future.traverse(Seq(claimValidationRepository))(_.collection.deleteMany(BsonDocument()).toFuture()),
      Inf
    )

    app.injector.instanceOf[ApplicationLifecycle].addStopHook { () =>
      Future(mongoComponent.client.close())
    }

  given headerCarrier: HeaderCarrier =
    HeaderCarrier(authorization = Option(Authorization("bearerToken"))).withExtraHeaders("Content-Type" -> "application/json")

  private val claimId   = "test-claim-id"
  private val reference = "test-reference-123"
  private val someFields = Some(Map("foo" -> "bar"))

  test("GET upload result returns 200 with AWAITING_UPLOAD status when document exists") {
    stubAuthenticate()

    val status = AwaitingUploadStatus(
      claimId = claimId,
      reference = reference,
      validationType = GiftAid,
      uploadUrl = "https://upload-url.com/bucket",
      initiateTimestamp = Instant.now(),
      fields = someFields,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    await(claimValidationRepository.insert(status))

    val httpClient = app.injector.instanceOf[HttpClientV2]
    val url        = routes.ClaimReferenceController.getUploadResult(claimId, reference).url
    val request    = httpClient.get(URI.create(s"http://localhost:$port$url").toURL)
    val result     = await(request.execute[HttpResponse])

    result.status shouldBe 200
    (result.json \ "reference").as[String] shouldBe reference
    (result.json \ "validationType").as[String] shouldBe "GiftAid"
    (result.json \ "fileStatus").as[String] shouldBe "AWAITING_UPLOAD"
    (result.json \ "uploadUrl").as[String] shouldBe "https://upload-url.com/bucket"
  }

  test("GET upload result returns 200 with VERIFYING status") {
    stubAuthenticate()

    val status = VerifyingStatus(
      claimId = claimId,
      reference = reference,
      validationType = ValidationType.OtherIncome,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    await(claimValidationRepository.insert(status))

    val httpClient = app.injector.instanceOf[HttpClientV2]
    val url        = routes.ClaimReferenceController.getUploadResult(claimId, reference).url
    val request    = httpClient.get(URI.create(s"http://localhost:$port$url").toURL)
    val result     = await(request.execute[HttpResponse])

    result.status shouldBe 200
    (result.json \ "fileStatus").as[String] shouldBe "VERIFYING"
    (result.json \ "validationType").as[String] shouldBe "OtherIncome"
    (result.json \ "uploadUrl").asOpt[String] shouldBe None
  }

  test("GET upload result returns 200 with VERIFICATION_FAILED status and failure details") {
    stubAuthenticate()

    val status = VerificationFailedStatus(
      claimId = claimId,
      reference = reference,
      validationType = GiftAid,
      failureDetails = FailureDetails(FailureReason.Quarantine, "This file has a virus"),
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    await(claimValidationRepository.insert(status))

    val httpClient = app.injector.instanceOf[HttpClientV2]
    val url        = routes.ClaimReferenceController.getUploadResult(claimId, reference).url
    val request    = httpClient.get(URI.create(s"http://localhost:$port$url").toURL)
    val result     = await(request.execute[HttpResponse])

    result.status shouldBe 200
    (result.json \ "fileStatus").as[String] shouldBe "VERIFICATION_FAILED"
    (result.json \ "failureDetails" \ "failureReason").as[String] shouldBe "QUARANTINE"
    (result.json \ "failureDetails" \ "message").as[String] shouldBe "This file has a virus"
  }

  test("GET upload result returns 200 with VALIDATING status") {
    stubAuthenticate()

    val status = ValidatingStatus(
      claimId = claimId,
      reference = reference,
      validationType = ValidationType.CommunityBuildings,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    await(claimValidationRepository.insert(status))

    val httpClient = app.injector.instanceOf[HttpClientV2]
    val url        = routes.ClaimReferenceController.getUploadResult(claimId, reference).url
    val request    = httpClient.get(URI.create(s"http://localhost:$port$url").toURL)
    val result     = await(request.execute[HttpResponse])

    result.status shouldBe 200
    (result.json \ "fileStatus").as[String] shouldBe "VALIDATING"
    (result.json \ "validationType").as[String] shouldBe "CommunityBuildings"
  }

  test("GET upload result returns 200 with VALIDATED status and gift aid schedule data") {
    stubAuthenticate()

    val giftAidData = GiftAidScheduleData(
      earliestDonationDate = Some(java.time.LocalDate.of(2025, 1, 31)),
      prevOverclaimedGiftAid = Some(BigDecimal(0)),
      totalDonations = Some(BigDecimal(1450)),
      donations = List(
        GiftAidDonation(
          donationItem = 1,
          donorTitle = Some("Mr"),
          donorFirstName = Some("John"),
          donorLastName = Some("Smith"),
          donorHouse = Some("123"),
          donorPostcode = Some("AB1 2CD"),
          aggregatedDonations = None,
          sponsoredEvent = false,
          donationDate = java.time.LocalDate.of(2025, 3, 24),
          donationAmount = BigDecimal(240)
        )
      )
    )

    val status = ValidatedStatus(
      claimId = claimId,
      reference = reference,
      validationType = GiftAid,
      giftAidScheduleData = Some(giftAidData),
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    await(claimValidationRepository.insert(status))

    val httpClient = app.injector.instanceOf[HttpClientV2]
    val url        = routes.ClaimReferenceController.getUploadResult(claimId, reference).url
    val request    = httpClient.get(URI.create(s"http://localhost:$port$url").toURL)
    val result     = await(request.execute[HttpResponse])

    result.status shouldBe 200
    (result.json \ "fileStatus").as[String] shouldBe "VALIDATED"
    (result.json \ "giftAidScheduleData" \ "totalDonations").as[BigDecimal] shouldBe BigDecimal(1450)
    (result.json \ "giftAidScheduleData" \ "donations").as[Seq[play.api.libs.json.JsValue]].size shouldBe 1
  }

  test("GET upload result returns 200 with VALIDATION_FAILED status and errors") {
    stubAuthenticate()

    val errors = Seq(
      ValidationError("earliestDonationDate", "ERROR: Earliest donation date is missing."),
      ValidationError("donations[0]", "ERROR: Item You have entered data in an invalid area of the form.")
    )

    val status = ValidationFailedStatus(
      claimId = claimId,
      reference = reference,
      validationType = GiftAid,
      giftAidScheduleData = Some(GiftAidScheduleData(None, None, None, List.empty)),
      errors = errors,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    await(claimValidationRepository.insert(status))

    val httpClient = app.injector.instanceOf[HttpClientV2]
    val url        = routes.ClaimReferenceController.getUploadResult(claimId, reference).url
    val request    = httpClient.get(URI.create(s"http://localhost:$port$url").toURL)
    val result     = await(request.execute[HttpResponse])

    result.status shouldBe 200
    (result.json \ "fileStatus").as[String] shouldBe "VALIDATION_FAILED"
    (result.json \ "errors").as[Seq[play.api.libs.json.JsValue]].size shouldBe 2
    ((result.json \ "errors")(0) \ "field").as[String] shouldBe "earliestDonationDate"
  }

  test("GET upload result returns 404 when reference does not exist") {
    stubAuthenticate()

    val httpClient = app.injector.instanceOf[HttpClientV2]
    val url        = routes.ClaimReferenceController.getUploadResult(claimId, "non-existent-ref").url
    val request    = httpClient.get(URI.create(s"http://localhost:$port$url").toURL)
    val result     = await(request.execute[HttpResponse])

    result.status shouldBe 404
    (result.json \ "error").as[String] shouldBe "CLAIM_REFERENCE_DOES_NOT_EXIST"
  }

  test("GET upload result returns 404 and deletes document when AWAITING_UPLOAD is expired (7+ days old)") {
    stubAuthenticate()

    val expiredTimestamp = Instant.now().minusSeconds(60 * 60 * 24 * 8) // 8 days ago
    val status = AwaitingUploadStatus(
      claimId = claimId,
      reference = reference,
      validationType = GiftAid,
      uploadUrl = "https://upload-url.com/bucket",
      initiateTimestamp = expiredTimestamp,
      fields = someFields,
      createdAt = expiredTimestamp,
      updatedAt = expiredTimestamp
    )
    await(claimValidationRepository.insert(status))

    val httpClient = app.injector.instanceOf[HttpClientV2]
    val url        = routes.ClaimReferenceController.getUploadResult(claimId, reference).url
    val request    = httpClient.get(URI.create(s"http://localhost:$port$url").toURL)
    val result     = await(request.execute[HttpResponse])

    result.status shouldBe 404
    (result.json \ "error").as[String] shouldBe "CLAIM_REFERENCE_HAS_EXPIRED"

    // Verify the document was deleted
    val afterDelete = await(claimValidationRepository.findByReference(claimId, reference))
    afterDelete shouldBe None
  }
}
