name := "signalement-api"
organization := "fr.gouv.beta"

version := "1.3"

scalaVersion := "2.12.10"

lazy val `signalement-api` = (project in file(".")).enablePlugins(PlayScala)

val playSlickVersion        = "4.0.2"
val slickPgVersion          = "0.19.0"
val playMailerVersion       = "8.0.1"
val playSilhouetteVersion   = "6.1.1"

libraryDependencies ++= Seq(
  guice,
  evolutions,
  ws,
  ehcache,

  "org.postgresql" % "postgresql" % "42.2.14",
  "com.typesafe.play" %% "play-slick" %  playSlickVersion,
  "com.typesafe.play" %% "play-slick-evolutions" % playSlickVersion,
  "com.github.tminglei" %% "slick-pg" % slickPgVersion,
  "com.github.tminglei" %% "slick-pg_play-json" % slickPgVersion,

  "com.typesafe.play" %% "play-mailer" % playMailerVersion,
  "com.typesafe.play" %% "play-mailer-guice" % playMailerVersion,

  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "2.0.1",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.835",

  "com.mohiva" %% "play-silhouette" % playSilhouetteVersion,
  "com.mohiva" %% "play-silhouette-password-bcrypt" % playSilhouetteVersion,
  "com.mohiva" %% "play-silhouette-persistence" % playSilhouetteVersion,
  "com.mohiva" %% "play-silhouette-crypto-jca" % playSilhouetteVersion,
  "com.mohiva" %% "play-silhouette-testkit" % playSilhouetteVersion % "test",
  "net.codingwell" %% "scala-guice" % "4.2.7",
  "com.iheart" %% "ficus" % "1.4.7",

  "com.norbitltd" %% "spoiwo" % "1.8.0",
  "com.itextpdf" % "itext7-core" % "7.1.11",
  "com.itextpdf" % "html2pdf" % "3.0.0",

  specs2 % Test,
  "org.specs2" %% "specs2-matcher-extra" % "4.10.0" % Test,
  "org.scalacheck" %% "scalacheck" % "1.14.3" % Test,

  "io.sentry" % "sentry-logback" % "1.7.30",
)

resolvers += "Atlassian Releases" at "https://maven.atlassian.com/public/"

mappings in Universal ++=
  (baseDirectory.value / "appfiles" * "*" get) map
    (x => x -> ("appfiles/" + x.getName))

javaOptions in Test += "-Dconfig.resource=test.application.conf"
