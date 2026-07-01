/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.controllers.utils

import play.api.libs.json.*
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.ErrorResults.badRequest
import uk.gov.hmrc.charitiesclaimsvalidation.models.responses.ErrorResponse

object JsonResultOps:

  private def firstRequiredEmptyParam(
    errs: scala.collection.Seq[(JsPath, scala.collection.Seq[JsonValidationError])]
  ): Option[String] =
    errs.iterator
      .flatMap { case (_, ves) => ves.iterator }
      .collectFirst {
        case e if e.message == "REQUIRED_PARAM_IS_EMPTY" =>
          e.args.headOption.map(_.toString).getOrElse("param")
      }

  extension [A](res: JsResult[A])
    def foldWithRequiredParam(
      onRequiredEmpty: String => ErrorResponse,
      onOtherInvalid: String => ErrorResponse
    )(onValid: A => Future[Result])(using ec: ExecutionContext): Future[Result] =
      res.fold(
        invalid = errs => {
          firstRequiredEmptyParam(errs) match
            case Some(param) => Future.successful(badRequest(onRequiredEmpty(param)))
            case None        => Future.successful(badRequest(onOtherInvalid(errs.mkString(", "))))
        },
        valid = onValid
      )
