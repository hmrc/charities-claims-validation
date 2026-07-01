/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.models.domain.errors

sealed trait ApplicationErrors extends Exception

case object NoRowsFoundException extends ApplicationErrors

case class BadSheetNameException(
  message: String = "The selected file must use the template required"
) extends Exception(message)
    with ApplicationErrors
