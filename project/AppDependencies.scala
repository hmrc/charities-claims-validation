import sbt.*

object AppDependencies {

  private val bootstrapVersion  = "10.7.0"
  private val catsEffectVersion = "3.6.3"
  private val catsVersion       = "2.13.0"
  private val hmrcMongoVersion  = "2.12.0"
  private val scalaMockVersion  = "7.5.5"
  private val scalaCheckVersion  = "3.2.19.0"
  private val scalaGenVersion  = "1.1.0"

  val compile: Seq[ModuleID] = Seq(
    "org.typelevel"     %% "cats-core"                 % catsVersion,
    "org.typelevel"     %% "cats-effect"               % catsEffectVersion,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "crypto-json-play-30"       % "8.4.0",
    "eu.timepit"        %% "refined"                   % "0.11.3"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % Test,
    "org.scalamock"          %% "scalamock"               % scalaMockVersion % Test,
    "org.scalatestplus"      %% "scalacheck-1-18"         % scalaCheckVersion % Test,
    "io.github.wolfendale"   %% "scalacheck-gen-regexp"   % scalaGenVersion % Test,
  )

  val it: Seq[ModuleID] = Seq.empty
}
