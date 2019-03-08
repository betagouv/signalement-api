name := "signalement-api"
organization := "fr.gouv.beta"

version := "0.1"

scalaVersion := "2.12.7"

lazy val `signalement-api` = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  guice,
  evolutions,
  ws,
  ehcache,

  "org.postgresql" % "postgresql" % "42.2.2",
  "com.typesafe.play" %% "play-slick" %  "3.0.2",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.2",

  "com.typesafe.play" %% "play-mailer" % "6.0.1",
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.1",

  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "0.20",

  specs2 % Test,

  "io.sentry" % "sentry-logback" % "1.7.14",
)

mappings in Universal ++=
  (baseDirectory.value / "appfiles" * "*" get) map
    (x => x -> ("appfiles/" + x.getName))