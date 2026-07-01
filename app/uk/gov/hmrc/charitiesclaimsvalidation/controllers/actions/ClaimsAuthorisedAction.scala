/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.controllers.actions

import com.google.inject.ImplementedBy
import play.api.mvc.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.charitiesclaimsvalidation.models.requests.AuthorisedRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultClaimsAuthorisedAction])
trait ClaimsAuthorisedAction extends ActionBuilder[AuthorisedRequest, AnyContent] with ActionFunction[Request, AuthorisedRequest]

@Singleton
class DefaultClaimsAuthorisedAction @Inject() (
  override val authConnector: AuthConnector,
  val parser: BodyParsers.Default
)(implicit val executionContext: ExecutionContext)
    extends ClaimsAuthorisedAction
    with AuthorisedFunctions {

  override def invokeBlock[A](
    request: Request[A],
    block: AuthorisedRequest[A] => Future[Result]
  ): Future[Result] = {

    given HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    authorised().retrieve(Retrievals.affinityGroup.and(Retrievals.allEnrolments)) {
      case Some(affinityGroup @ AffinityGroup.Agent) ~ AuthorisedAction.HasActiveAgentEnrolment() =>
        block(AuthorisedRequest(request, affinityGroup))

      case Some(AffinityGroup.Agent) ~ _ =>
        Future.failed(UnsupportedAffinityGroup("Agent enrolment missing or not activated"))

      case Some(affinityGroup @ AffinityGroup.Organisation) ~ AuthorisedAction.HasActiveOrganisationEnrolment() =>
        block(AuthorisedRequest(request, affinityGroup))

      case Some(AffinityGroup.Organisation) ~ _ =>
        Future.failed(UnsupportedAffinityGroup("Organisation enrolment missing or not activated"))

      case _ =>
        Future.failed(InternalError("Missing Agent and Organisation enrolments"))
    }
  }
}

object AuthorisedAction {
  val ORG_ENROLMENT_KEY     = "HMRC-CHAR-ORG"
  val ORG_IDENTIFIER_NAME   = "CHARID"
  val AGENT_ENROLMENT_KEY   = "HMRC-CHAR-AGENT"
  val AGENT_IDENTIFIER_NAME = "AGENTCHARID"

  private def hasActiveEnrolment(
    enrolments: Enrolments,
    enrolmentKey: String,
    identifierName: String
  ): Boolean =
    enrolments
      .getEnrolment(enrolmentKey)
      .exists(e => e.isActivated && e.getIdentifier(identifierName).isDefined)

  object HasActiveAgentEnrolment {
    def unapply(enrolments: Enrolments): Boolean =
      hasActiveEnrolment(enrolments, AGENT_ENROLMENT_KEY, AGENT_IDENTIFIER_NAME)
  }

  object HasActiveOrganisationEnrolment {
    def unapply(enrolments: Enrolments): Boolean =
      hasActiveEnrolment(enrolments, ORG_ENROLMENT_KEY, ORG_IDENTIFIER_NAME)
  }
}
