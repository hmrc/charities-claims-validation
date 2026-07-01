/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.charitiesclaimsvalidation.helpers.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

trait WireMockServerHandler extends BeforeAndAfterAll with BeforeAndAfterEach {
  this: Suite =>

  val wiremockPort = 11223

  protected val server: WireMockServer = new WireMockServer(
    wireMockConfig.port(wiremockPort)
  )

  override protected def beforeAll(): Unit = {
    server.start()
    super.beforeAll()
  }

  override protected def beforeEach(): Unit = {
    server.resetAll()
    super.beforeEach()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }
}