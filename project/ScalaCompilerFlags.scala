object ScalaCompilerFlags {
  val scalaCompilerOptions: Seq[String] = Seq(
    "-explain-cyclic",
    "-language:implicitConversions",
    "-language:reflectiveCalls",
    "-Wconf:msg=While parsing annotations in:silent",
    "-Wconf:src=routes/.*:s",
    "-Wconf:src=.*conf/.*\\.routes:silent", // Suppress warnings specifically for .routes files in conf directory
    "-Wconf:src=target/.*:s",
    "-Xfatal-warnings",
    "-feature",
    "-Xfatal-warnings",
    "-deprecation",
    "-unchecked"
  )
}
