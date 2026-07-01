/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.controllers.actions

import com.google.inject.Inject
import play.api.mvc.*

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.charitiesclaimsvalidation.models.requests.AuthorisedRequest

class FakeClaimsAuthorisedAction @Inject() (cc: ControllerComponents)(using ec: ExecutionContext) extends ClaimsAuthorisedAction {

  override def parser: BodyParser[AnyContent]               = cc.parsers.defaultBodyParser
  override protected def executionContext: ExecutionContext = ec

  override def invokeBlock[A](request: Request[A], block: AuthorisedRequest[A] => Future[Result]): Future[Result] = {
    val authRequest = new AuthorisedRequest[A](request, AffinityGroup.Organisation)
    block(authRequest)
  }

}
