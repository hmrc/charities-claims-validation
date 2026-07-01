/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.requests

import play.api.mvc.{Request, WrappedRequest}
import uk.gov.hmrc.auth.core.AffinityGroup

final case class AuthorisedRequest[A](
  underlying: Request[A],
  affinityGroup: AffinityGroup
) extends WrappedRequest[A](underlying)
