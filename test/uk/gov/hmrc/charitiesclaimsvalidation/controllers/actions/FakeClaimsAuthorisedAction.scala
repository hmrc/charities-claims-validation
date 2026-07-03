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
