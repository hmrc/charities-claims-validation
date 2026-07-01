/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.util

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, matchers}
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.{Json, Writes}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.charitiesclaimsvalidation.controllers.actions.DefaultClaimsAuthorisedAction
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait ControllerSpec extends AnyWordSpec with Matchers with MockFactory with BeforeAndAfterAll {

  implicit val actorSystem: ActorSystem = ActorSystem("test")
  implicit val mat: Materializer        = Materializer(actorSystem)
  implicit val ec: ExecutionContext     = scala.concurrent.ExecutionContext.global

  def jsonRequest[A: Writes](method: String, url: String, body: A): FakeRequest[play.api.mvc.AnyContentAsJson] =
    FakeRequest(method, url)
      .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
      .withJsonBody(Json.toJson(body))

  def emptyRequest(method: String, url: String): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(method, url)

  override protected def afterAll(): Unit =
    actorSystem.terminate(): Unit

  trait AuthorisedFixture {
    val mockAuthConnector: AuthConnector = mock[AuthConnector]

    val orgEnrolment = Enrolment(
      key = "HMRC-CHAR-ORG",
      identifiers = Seq(EnrolmentIdentifier("CHARID", "1234567890")),
      state = "Activated"
    )

    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[Option[AffinityGroup] ~ Enrolments])(using
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .expects(*, *, *, *)
      .anyNumberOfTimes()
      .returning(
        Future.successful(
          new ~(
            Some(AffinityGroup.Organisation),
            Enrolments(Set(orgEnrolment))
          )
        )
      )

    val bodyParser       = play.api.mvc.BodyParsers.Default(play.api.test.Helpers.stubPlayBodyParsers)
    val authorisedAction = new DefaultClaimsAuthorisedAction(mockAuthConnector, bodyParser)
  }
}
