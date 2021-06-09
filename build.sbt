name := "signalement-api"
organization := "fr.gouv.beta"

version := "1.3.13"

scalaVersion := "2.12.14"

lazy val `signalement-api` = (project in file(".")).enablePlugins(PlayScala)

val playSlickVersion        = "5.0.0"
val slickPgVersion          = "0.19.6"
val playMailerVersion       = "8.0.1"
val playSilhouetteVersion   = "7.0.0"
val AkkaHttpVersion = "10.1.14"

libraryDependencies ++= Seq(
  guice,
  evolutions,
  ws,
  ehcache,

  "org.postgresql" % "postgresql" % "42.2.20",
  "com.typesafe.play" %% "play-slick" %  playSlickVersion,
  "com.typesafe.play" %% "play-slick-evolutions" % playSlickVersion,
  "com.github.tminglei" %% "slick-pg" % slickPgVersion,
  "com.github.tminglei" %% "slick-pg_play-json" % slickPgVersion,

  "com.typesafe.play" %% "play-mailer" % playMailerVersion,
  "com.typesafe.play" %% "play-mailer-guice" % playMailerVersion,

  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "3.0.1",
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-xml" % AkkaHttpVersion,
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.889",

  "com.mohiva" %% "play-silhouette" % playSilhouetteVersion,
  "com.mohiva" %% "play-silhouette-password-bcrypt" % playSilhouetteVersion,
  "com.mohiva" %% "play-silhouette-persistence" % playSilhouetteVersion,
  "com.mohiva" %% "play-silhouette-crypto-jca" % playSilhouetteVersion,
  "com.mohiva" %% "play-silhouette-testkit" % playSilhouetteVersion % "test",
  "net.codingwell" %% "scala-guice" % "5.0.1",
  "com.iheart" %% "ficus" % "1.5.0",

  "com.norbitltd" %% "spoiwo" % "1.8.0",
  "com.itextpdf" % "itext7-core" % "7.1.15",
  "com.itextpdf" % "html2pdf" % "3.0.3",

  specs2 % Test,
  "org.specs2" %% "specs2-matcher-extra" % "4.10.5" % Test,
  "org.scalacheck" %% "scalacheck" % "1.15.4" % Test,

  "io.sentry" % "sentry-logback" % "5.0.0",

  "org.typelevel" %% "cats-core" % "2.6.1"

)

resolvers += "Atlassian Releases" at "https://maven.atlassian.com/public/"

mappings in Universal ++=
  (baseDirectory.value / "appfiles" * "*" get) map
    (x => x -> ("appfiles/" + x.getName))

javaOptions in Test += "-Dconfig.resource=test.application.conf"
javaOptions += "-Dakka.http.parsing.max-uri-length=16k"
