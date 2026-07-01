/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.controllers

import org.mockito.Mockito.*
import org.mongodb.scala.MongoException
import org.scalatest.matchers.must.Matchers.*
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.charitiesclaimsvalidation.controllers.actions.FakeClaimsAuthorisedAction
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.ValidationType
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.{SuccessResponse, UploadSummaryItem, UploadSummaryResponse}
import uk.gov.hmrc.charitiesclaimsvalidation.services.{ClaimService, UploadSummaryService}
import uk.gov.hmrc.charitiesclaimsvalidation.util.{AllMocks, BaseSpec}
import org.mockito.ArgumentMatchers.any
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.JsValue
import play.api.mvc.Results.{Created, Forbidden}
import play.api.mvc.{AnyContentAsJson, Result}
import uk.gov.hmrc.charitiesclaimsvalidation.generators.ModelGenerators
import uk.gov.hmrc.charitiesclaimsvalidation.models.requests.UploadRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ClaimControllerSpec extends BaseSpec with MockitoSugar with ScalaCheckPropertyChecks with ModelGenerators with AllMocks {
  given ExecutionContext = global
  private val claimId    = "12345"

  trait Fixture {
    val cc                                             = Helpers.stubControllerComponents()
    val mockClaimsService: ClaimService                = mock[ClaimService]
    val mockUploadSummaryService: UploadSummaryService = mock[UploadSummaryService]
    val fakeAuth                                       = new FakeClaimsAuthorisedAction(cc)
    val controller                                     = new ClaimController(cc, fakeAuth, mockClaimsService, mockUploadSummaryService)
  }

  "ClaimController " - {

    "deleteClaims" - {
      "return ok with success as true when claims are deleted successfully" in new Fixture {
        when(mockClaimsService.deleteClaims(claimId)).thenReturn(Future.successful(SuccessResponse(true)))
        val result = controller.deleteClaims(claimId)(FakeRequest())

        status(result) mustBe Status.OK
        contentAsJson(result) mustBe Json.toJson(SuccessResponse(true))
      }

      "throw error when mongo error thrown" in new Fixture {
        val mongoEx = new MongoException("mongo delete failed")
        when(mockClaimsService.deleteClaims(claimId)).thenReturn(Future.failed(mongoEx))
        whenReady(controller.deleteClaims(claimId)(FakeRequest()).failed) { ex =>
          ex mustBe mongoEx
        }
      }
    }

    "uploadTracking" - {
      "should return BAD_REQUEST when upload body is invalid" in new Fixture {

        val request: FakeRequest[AnyContentAsJson] =
          FakeRequest(
            POST,
            routes.ClaimController.uploadTracking("id").url
          )
            .withJsonBody(Json.parse("""{"value": "field"}"""))

        val result: Future[Result] = call(controller.uploadTracking(claimId), request)
        status(result) mustEqual BAD_REQUEST
      }

      "should return FORBIDDEN when authorisation is invalid" in new Fixture {
        when(
          mockClaimsService
            .storeUploadStatus(
              any[String](),
              any[UploadRequest]()
            )
        )
          .thenReturn(
            Future.successful(
              Forbidden("Not authorised")
            )
          )

        forAll(arbitrary[UploadRequest]) { uploadRequest =>
          val request =
            FakeRequest(
              POST,
              routes.ClaimController.uploadTracking("id").url
            ).withJsonBody(Json.toJson(uploadRequest))

          val result = call(controller.uploadTracking(claimId), request)
          status(result) mustEqual FORBIDDEN
        }
      }

      "should return a 201 when the upload is successfully stored in the DB" in new Fixture {
        when(
          mockClaimsService
            .storeUploadStatus(
              any[String](),
              any[UploadRequest]()
            )
        )
          .thenReturn(
            Future.successful(
              Created("Successfully stored upload status")
            )
          )

        forAll(arbitrary[UploadRequest]) { uploadRequest =>
          val request =
            FakeRequest(
              POST,
              routes.ClaimController.uploadTracking("id").url
            ).withJsonBody(Json.toJson(uploadRequest))

          val result = call(controller.uploadTracking(claimId), request)
          status(result) mustEqual CREATED
        }
      }

    }

    "getUploadSummary" - {
      val testClaimId = "test-claim-123"

      "return 200 OK with upload summary when uploads exist" in new Fixture {
        val summaryItem = UploadSummaryItem(
          reference = "ref-uuid-001",
          validationType = ValidationType.GiftAid,
          fileStatus = "VALIDATED",
          uploadUrl = None
        )
        val response = UploadSummaryResponse(Seq(summaryItem))

        when(mockUploadSummaryService.getUploadSummary(testClaimId)).thenReturn(Future.successful(Some(response)))

        val result = controller.getUploadSummary(testClaimId)(FakeRequest())

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.obj(
          "uploads" -> Json.arr(
            Json.obj(
              "reference"      -> "ref-uuid-001",
              "validationType" -> "GiftAid",
              "fileStatus"     -> "VALIDATED"
            )
          )
        )
      }

      "return 200 OK with multiple uploads" in new Fixture {
        val summaryItems = Seq(
          UploadSummaryItem("ref-1", ValidationType.GiftAid, "VALIDATED", None),
          UploadSummaryItem("ref-2", ValidationType.OtherIncome, "VALIDATING", None)
        )
        val response = UploadSummaryResponse(summaryItems)

        when(mockUploadSummaryService.getUploadSummary(testClaimId)).thenReturn(Future.successful(Some(response)))

        val result = controller.getUploadSummary(testClaimId)(FakeRequest())

        status(result) mustBe OK
        val json = contentAsJson(result)
        (json \ "uploads").as[Seq[play.api.libs.json.JsValue]] must have size 2
      }

      "return 200 OK with uploadUrl when status is AWAITING_UPLOAD" in new Fixture {
        val summaryItem = UploadSummaryItem(
          reference = "ref-uuid-001",
          validationType = ValidationType.GiftAid,
          fileStatus = "AWAITING_UPLOAD",
          uploadUrl = Some("https://upscan-upload-proxy/bucket"),
          fields = Some(Map("foo" -> "bar"))
        )
        val response = UploadSummaryResponse(Seq(summaryItem))

        when(mockUploadSummaryService.getUploadSummary(testClaimId)).thenReturn(Future.successful(Some(response)))

        val result = controller.getUploadSummary(testClaimId)(FakeRequest())

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.obj(
          "uploads" -> Json.arr(
            Json.obj(
              "reference"      -> "ref-uuid-001",
              "validationType" -> "GiftAid",
              "fileStatus"     -> "AWAITING_UPLOAD",
              "uploadUrl"      -> "https://upscan-upload-proxy/bucket",
              "fields" -> Json.obj(
                "foo" -> "bar"
              )
            )
          )
        )
      }

      "return 404 NOT_FOUND when no uploads exist for the claim" in new Fixture {
        when(mockUploadSummaryService.getUploadSummary(testClaimId)).thenReturn(Future.successful(None))

        val result = controller.getUploadSummary(testClaimId)(FakeRequest())

        status(result) mustBe NOT_FOUND
        contentAsJson(result) mustBe Json.obj(
          "error"   -> "CLAIM_DOES_NOT_EXIST",
          "message" -> s"There is no claim found for the given claimId=$testClaimId"
        )
      }

      "return 500 INTERNAL_SERVER_ERROR when service throws exception" in new Fixture {
        when(mockUploadSummaryService.getUploadSummary(testClaimId)).thenReturn(Future.failed(new MongoException("mongo connection failed")))

        val result = controller.getUploadSummary(testClaimId)(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "error"   -> "INTERNAL_SERVER_ERROR",
          "message" -> "Currently experiencing issues with our service"
        )
      }
    }

    "touchTtl" - {

      "return 204 NO_CONTENT when TTL update succeeds" in new Fixture {
        when(mockClaimsService.touchTtlByClaimId(claimId))
          .thenReturn(Future.unit)

        val result = controller.touchTtl(claimId)(FakeRequest())

        status(result) mustBe NO_CONTENT
        contentAsString(result) mustBe empty
      }
    }
  }
}
