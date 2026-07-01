/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation

import cats.effect.unsafe.IORuntime
import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module as AppModule}

import java.time.Clock

class Module extends AppModule:

  override def bindings(
    environment: Environment,
    configuration: Configuration
  ): Seq[Binding[_]] =
    Seq(
      bind[Clock].toInstance(Clock.systemDefaultZone),
      bind[IORuntime].toInstance(IORuntime.global)
    )
