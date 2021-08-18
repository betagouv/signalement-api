name := "signalement-api"
organization := "fr.gouv.beta"

version := "1.3.13"

scalaVersion := "2.12.10"

lazy val `signalement-api` = (project in file(".")).enablePlugins(PlayScala)

val playSlickVersion = "5.0.0"
val slickPgVersion = "0.19.5"
val playMailerVersion = "8.0.1"
val playSilhouetteVersion = "7.0.0"
val AkkaHttpVersion = "10.1.12"
val alpakkaVersion = "3.0.3"

libraryDependencies ++= Seq(
  guice,
  evolutions,
  ws,
  ehcache,
  "org.postgresql" % "postgresql" % "42.2.19",
  "com.typesafe.play" %% "play-slick" % playSlickVersion,
  "com.typesafe.play" %% "play-slick-evolutions" % playSlickVersion,
  "com.github.tminglei" %% "slick-pg" % slickPgVersion,
  "com.github.tminglei" %% "slick-pg_play-json" % slickPgVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-slick" % alpakkaVersion,
  "com.typesafe.play" %% "play-mailer" % playMailerVersion,
  "com.typesafe.play" %% "play-mailer-guice" % playMailerVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % alpakkaVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-csv" % alpakkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-xml" % AkkaHttpVersion,
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.889",
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
  "org.typelevel" %% "cats-core" % "2.4.2"
)

scalafmtOnCompile := true

routesImport ++= Seq(
  "models.WebsiteKind",
  "controllers.WebsiteKindQueryStringBindable"
)

resolvers += "Atlassian Releases" at "https://maven.atlassian.com/public/"

mappings in Universal ++=
  (baseDirectory.value / "appfiles" * "*" get) map
    (x => x -> ("appfiles/" + x.getName))

javaOptions in Test += "-Dconfig.resource=test.application.conf"
javaOptions += "-Dakka.http.parsing.max-uri-length=16k"
