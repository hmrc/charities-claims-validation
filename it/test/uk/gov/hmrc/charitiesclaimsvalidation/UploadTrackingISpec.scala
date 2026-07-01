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
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.charitiesclaimsvalidation.controllers.routes
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.charitiesclaimsvalidation.helpers.wiremock.WireMockServerHandler
import uk.gov.hmrc.charitiesclaimsvalidation.helpers.AuthStubs
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.ValidationType.GiftAid
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.AwaitingUploadStatus
import uk.gov.hmrc.charitiesclaimsvalidation.repositories.ClaimValidationStatusRepository
import uk.gov.hmrc.mongo.MongoComponent

import java.net.URI
import java.time.Instant
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration.Inf

class UploadTrackingISpec
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

  test("Save upload status successfully") {
    stubAuthenticate()
    val example    = Json.parse(getClass.getResourceAsStream("/data/upload_request.json"))
    val exampleDBResponse = AwaitingUploadStatus("claimId", "f5da5578-8393-4cd1-be0e-d8ef1b78d8e7", GiftAid, "https://xxxx/upscan-upload-proxy/bucketName", Instant.now(), Some(Map("foo" -> "bar")), Instant.now(), Instant.now())
    val httpClient = app.injector.instanceOf[HttpClientV2]
    val url        = routes.ClaimController.uploadTracking("claimId").url
    val request    = httpClient.post(URI.create(s"http://localhost:$port$url").toURL).withBody(example)
    val result     = await(request.execute[HttpResponse])
    result.status shouldBe 201
    Seq(exampleDBResponse).size shouldBe 1
  }

  test("If Save upload status already exists and is an awaiting upload state then return 201 and same body") {
    stubAuthenticate()
    val example = Json.parse(getClass.getResourceAsStream("/data/upload_request.json"))
    val httpClient = app.injector.instanceOf[HttpClientV2]
    val url = routes.ClaimController.uploadTracking("claimId").url
    val request = httpClient.post(URI.create(s"http://localhost:$port$url").toURL).withBody(example)
    val result = await(request.execute[HttpResponse])
    val sameRequest = httpClient.post(URI.create(s"http://localhost:$port$url").toURL).withBody(example)
    val sameResult = await(sameRequest.execute[HttpResponse])
    result.status shouldBe 201
    sameResult.status shouldBe 201
    result.body shouldBe sameResult.body
  }

  test("Allow 2 separate users to be stored with the same validation type") {
    stubAuthenticate()
    val example = Json.parse(getClass.getResourceAsStream("/data/upload_request.json"))
    val httpClient = app.injector.instanceOf[HttpClientV2]
    val url = routes.ClaimController.uploadTracking("claimId").url
    val urlDiffId = routes.ClaimController.uploadTracking("claimId1").url
    val request = httpClient.post(URI.create(s"http://localhost:$port$url").toURL).withBody(example)
    val result = await(request.execute[HttpResponse])
    val sameRequest = httpClient.post(URI.create(s"http://localhost:$port$urlDiffId").toURL).withBody(example)
    val sameResult = await(sameRequest.execute[HttpResponse])
    result.status shouldBe 201
    sameResult.status shouldBe 201
  }

  test("Do not save if upload request body contains empty strings") {
    stubAuthenticate()
    val example = Json.parse(getClass.getResourceAsStream("/data/upload_request_empty_strings.json"))
    val httpClient = app.injector.instanceOf[HttpClientV2]
    val url = routes.ClaimController.uploadTracking("claimId").url
    val request = httpClient.post(URI.create(s"http://localhost:$port$url").toURL).withBody(example)
    val result = await(request.execute[HttpResponse])
    result.status shouldBe 400
  }

}
