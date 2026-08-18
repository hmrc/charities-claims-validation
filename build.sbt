import uk.gov.hmrc.DefaultBuildSettings
import play.sbt.PlayImport.PlayKeys.playDefaultPort

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.3.6"
ThisBuild / scalacOptions += "-Wconf:msg=Flag.*repeatedly:s"

lazy val appName: String = "charities-claims-validation"

lazy val microservice = Project("charities-claims-validation", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    name := appName,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    scalacOptions ++= ScalaCompilerFlags.scalaCompilerOptions,
    playDefaultPort := 8032,
    scalafmtOnCompile := true
)
  .settings(
    Test / unmanagedResourceDirectories := Seq(baseDirectory.value / "test-resources")
  )
  .settings(CodeCoverageSettings.settings: _*)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.it)

addCommandAlias("report", ";clean; coverage; test; it/test; coverageReport")
