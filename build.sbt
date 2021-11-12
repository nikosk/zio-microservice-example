import sbt.Keys.crossScalaVersions

val zioVersion = "1.0.12"
val zioHttpVersion = "1.0.0.0-RC17"
val zioSchemaVersion = "0.1.1"
val zioJsonVersion = "0.1.5"
val zioConfigVersion = "1.0.10"
val zioOpticsVersion = "0.1.0"
val zioPreludeVersion = "1.0.0-RC5"
val zioMagicVersion = "0.3.9"
val zioLoggingVersion = "0.5.13"

val typeSafeConfig = "1.4.1"
val jacksonVersion = "2.13.0"
val hikariVersion = "5.0.0"
val h2Version = "1.4.200"
val jdbiVersion = "3.23.0"
val logbackVersion = "1.2.6"

lazy val `zio-microservice-example` = project
  .in(file("."))
  .settings(
    inThisBuild(
      List(
        name := "zio-microservice-example",
        organization := "com.getlabeltext",
        version := "0.0.1",
        scalaVersion := "2.13.6",
        crossScalaVersions := Seq("2.13.6", "3.0.2")
      )
    ),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "io.d11" %% "zhttp" % zioHttpVersion,
      "dev.zio" %% "zio-json" % zioJsonVersion,
      "dev.zio" %% "zio-optics" % zioOpticsVersion,
      "dev.zio" %% "zio-prelude" % zioPreludeVersion,
      "dev.zio" %% "zio-logging-slf4j" % zioLoggingVersion,
      "dev.zio" %% "zio-logging-slf4j-bridge" % zioLoggingVersion,
      "io.github.kitlangton" %% "zio-magic" % zioMagicVersion,
      // Java libs
      "com.typesafe" % "config" % typeSafeConfig,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.zaxxer" % "HikariCP" % hikariVersion exclude("org.slf4j", "slf4j-api"),
      "org.jdbi" % "jdbi3-core" % jdbiVersion,
      "com.h2database" % "h2" % h2Version,
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "org.slf4j" % "slf4j-api" % "1.7.32",

      // Test Dependencies
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
      "dev.zio" %% "zio-test-junit" % zioVersion % Test,
      "dev.zio" %% "zio-test-magnolia" % zioVersion % Test
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

