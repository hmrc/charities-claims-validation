/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.formats

import eu.timepit.refined.types.all.NonEmptyString
import play.api.libs.json.*

object JsonImplicits:

  val RequiredParamIsEmpty = "REQUIRED_PARAM_IS_EMPTY"

  given bigDecimalWrites: Writes[BigDecimal] = Writes { value =>
    JsString(value.setScale(2, BigDecimal.RoundingMode.HALF_UP).toString)
  }

  given Reads[NonEmptyString] = Reads { js =>
    js.validate[String] match
      case JsSuccess(str, _) =>
        if str.trim.isEmpty then JsError(RequiredParamIsEmpty)
        else
          NonEmptyString.from(str.trim) match
            case Left(_)   => JsError(RequiredParamIsEmpty)
            case Right(ne) => JsSuccess(ne)
      case JsError(errs) =>
        JsError(errs)
  }

  given Writes[NonEmptyString] =
    Writes(str => JsString(str.value))

  extension (path: JsPath)
    def requiredNonEmpty(using Reads[NonEmptyString]): Reads[NonEmptyString] = {
      val param = path.path.collect { case KeyPathNode(k) => k }.lastOption.getOrElse("param")

      Reads { js =>
        path.asSingleJson(js) match
          case JsDefined(value) =>
            value.validate[NonEmptyString] match
              case ok @ JsSuccess(_, _) => ok
              case JsError(_) =>
                JsError(path, JsonValidationError(RequiredParamIsEmpty, param))
          case _: JsUndefined =>
            JsError(path, JsonValidationError(RequiredParamIsEmpty, param))
      }
    }
