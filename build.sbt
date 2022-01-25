name := "signalement-api"
organization := "fr.gouv.beta"

version := "1.3.13"

scalaVersion := "2.13.7"

lazy val `signalement-api` = (project in file(".")).enablePlugins(PlayScala)

val playSlickVersion = "5.0.0"
val slickPgVersion = "0.19.7"
val playMailerVersion = "8.0.1"
val playSilhouetteVersion = "7.0.0"
val AkkaHttpVersion = "10.1.14"
val alpakkaVersion = "2.0.2"

libraryDependencies ++= Seq(
  guice,
  evolutions,
  ws,
  ehcache,
  "org.postgresql" % "postgresql" % "42.2.19",
  "eu.timepit" %% "refined" % "0.9.28",
  "com.typesafe.play" %% "play-slick" % playSlickVersion,
  "com.typesafe.play" %% "play-slick-evolutions" % playSlickVersion,
  "com.github.tminglei" %% "slick-pg" % slickPgVersion,
  "com.github.tminglei" %% "slick-pg_play-json" % slickPgVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-slick" % alpakkaVersion,
  "com.typesafe.play" %% "play-mailer" % playMailerVersion,
  "com.typesafe.play" %% "play-mailer-guice" % playMailerVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % alpakkaVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-csv" % alpakkaVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-file" % alpakkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-xml" % AkkaHttpVersion,
  "com.amazonaws" % "aws-java-sdk-s3" % "1.12.146",
  "com.mohiva" %% "play-silhouette" % playSilhouetteVersion,
  "com.mohiva" %% "play-silhouette-password-bcrypt" % playSilhouetteVersion,
  "com.mohiva" %% "play-silhouette-persistence" % playSilhouetteVersion,
  "com.mohiva" %% "play-silhouette-crypto-jca" % playSilhouetteVersion,
  "com.mohiva" %% "play-silhouette-testkit" % playSilhouetteVersion % "test",
  "net.codingwell" %% "scala-guice" % "4.2.11",
  "com.iheart" %% "ficus" % "1.5.0",
  "com.norbitltd" %% "spoiwo" % "1.8.0",
  "com.itextpdf" % "itext7-core" % "7.1.14",
  "com.itextpdf" % "html2pdf" % "3.0.3",
  "com.beachape" %% "enumeratum" % "1.6.1",
  "com.beachape" %% "enumeratum-play" % "1.6.3",
  "com.beachape" %% "enumeratum-slick" % "1.6.0",
  "io.scalaland" %% "chimney" % "0.6.1",
  specs2 % Test,
  "org.specs2" %% "specs2-matcher-extra" % "4.10.5" % Test,
  "org.scalacheck" %% "scalacheck" % "1.15.3" % Test,
  "io.sentry" % "sentry-logback" % "1.7.30",
  "org.typelevel" %% "cats-core" % "2.4.2",
  "com.github.pureconfig" %% "pureconfig" % "0.17.0",
  compilerPlugin(scalafixSemanticdb)
)

scalafmtOnCompile := true
scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8", // Specify character encoding used by source files.
  "-explaintypes", // Explain type errors in more detail.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
  "-language:higherKinds", // Allow higher-kinded types
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
  "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
  "-Xlint:option-implicit", // Option.apply used implicit view.
  "-Xlint:package-object-classes", // Class or object defined in package object.
  "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
  "-Xlint:unused", // TODO check if we still need -Wunused below
  "-Xlint:nonlocal-return", // A return statement used an exception for flow control.
  "-Xlint:implicit-not-found", // Check @implicitNotFound and @implicitAmbiguous messages.
  "-Xlint:serial", // @SerialVersionUID on traits and non-serializable classes.
  "-Xlint:valpattern", // Enable pattern checks in val definitions.
  "-Xlint:eta-zero", // Warn on eta-expansion (rather than auto-application) of zero-ary method.
  "-Xlint:eta-sam", // Warn on eta-expansion to meet a Java-defined functional interface that is not explicitly annotated with @FunctionalInterface.
  "-Xlint:deprecation", // Enable linted deprecations.
  "-Ywarn-unused",
  "-Ywarn-macros:after",
  "-Ywarn-unused:params",
  "-Ywarn-unused:implicits",
  "-Ywarn-unused:patvars",
  "-Wconf:cat=unused-imports&src=views/.*:s",
  "-Wconf:cat=unused:info",
  s"-Wconf:src=${target.value}/.*:s",
  "-Yrangepos"
)

routesImport ++= Seq(
  "models.website.WebsiteKind",
  "models.ReportResponseType",
  "controllers.WebsiteKindQueryStringBindable",
  "controllers.ReportResponseTypeQueryStringBindable"
)

scalafixOnCompile := true

//ThisBuild / coverageEnabled := true

resolvers += "Atlassian Releases" at "https://packages.atlassian.com/maven-public/"

Universal / mappings ++=
  (baseDirectory.value / "appfiles" * "*" get) map
    (x => x -> ("appfiles/" + x.getName))

Test / javaOptions += "-Dconfig.resource=test.application.conf"
javaOptions += "-Dakka.http.parsing.max-uri-length=16k"
