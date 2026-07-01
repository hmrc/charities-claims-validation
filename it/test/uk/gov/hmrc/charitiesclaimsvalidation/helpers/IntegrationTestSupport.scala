/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.helpers

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.charitiesclaimsvalidation.repositories.ClaimValidationStatusRepository
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.Future

class MockAuthConnector extends AuthConnector:
  override def authorise[A](
    predicate: uk.gov.hmrc.auth.core.authorise.Predicate,
    retrieval: uk.gov.hmrc.auth.core.retrieve.Retrieval[A]
  )(implicit hc: HeaderCarrier, ec: scala.concurrent.ExecutionContext): Future[A] =
    Future.successful(
      `~`(
        Some(AffinityGroup.Organisation),
        Enrolments(Set(Enrolment("HMRC-CHAR-ORG", Seq(EnrolmentIdentifier("CHARID", "1234567890")), "Activated")))
      ).asInstanceOf[A]
    )

trait IntegrationTestSupport
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with GuiceOneServerPerSuite
    with MongoSupport
    with BeforeAndAfterAll
    with BeforeAndAfterEach:

  lazy val mockAuthConnector: AuthConnector = MockAuthConnector()

  lazy val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
  lazy val repository: ClaimValidationStatusRepository = app.injector.instanceOf[ClaimValidationStatusRepository]
  def baseUrl: String = s"http://localhost:$port/charities-claims-validation"

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure("mongodb.uri" -> mongoUri)
      .overrides(
        bind[AuthConnector].toInstance(mockAuthConnector),
        bind[MongoComponent].toInstance(mongoComponent)
      )
      .build()

  override def beforeAll(): Unit =
    dropDatabase()

  override def afterAll(): Unit =
    dropDatabase()
