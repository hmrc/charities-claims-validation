/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.config

import play.api.Configuration

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.*

@Singleton
class AppConfig @Inject() (config: Configuration):

  lazy val replaceIndexes: Boolean = config.get[Boolean]("mongodb.replaceIndexes")
  lazy val ttl: Int                = config.get[Int]("mongodb.timeToLiveInSeconds")
  lazy val isTtlEnabled: Boolean   = config.get[Boolean]("mongodb.isTtlEnabled")

  lazy val retryMaxAttempts: Int             = config.get[Int]("mongodb.retry.maxAttempts")
  lazy val retryInitialDelay: FiniteDuration = config.get[Int]("mongodb.retry.initialDelayMs").millis
  lazy val retryMaxDelay: FiniteDuration     = config.get[Int]("mongodb.retry.maxDelayMs").millis
