name := "signalement-api"
organization := "fr.gouv.beta"

version := "1.3"

scalaVersion := "2.12.8"

lazy val `signalement-api` = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  guice,
  evolutions,
  ws,
  ehcache,

  "org.postgresql" % "postgresql" % "42.2.2",
  "com.typesafe.play" %% "play-slick" %  "3.0.2",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.2",
  "com.github.tminglei" %% "slick-pg" % "0.17.2",

  "com.typesafe.play" %% "play-mailer" % "6.0.1",
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.1",

  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "0.20",

  "com.mohiva" %% "play-silhouette" % "5.0.7",
  "com.mohiva" %% "play-silhouette-password-bcrypt" % "5.0.7",
  "com.mohiva" %% "play-silhouette-persistence" % "5.0.7",
  "com.mohiva" %% "play-silhouette-crypto-jca" % "5.0.7",
  "com.mohiva" %% "play-silhouette-testkit" % "5.0.7" % "test",
  "net.codingwell" %% "scala-guice" % "4.1.1",
  "com.iheart" %% "ficus" % "1.4.3",

  "com.norbitltd" %% "spoiwo" % "1.4.1",

  "com.itextpdf" % "itext7-core" % "7.1.6",
  "com.itextpdf" % "html2pdf" % "2.1.3",

  specs2 % Test,

  "io.sentry" % "sentry-logback" % "1.7.14",
)

resolvers += "Atlassian Releases" at "https://maven.atlassian.com/public/"

mappings in Universal ++=
  (baseDirectory.value / "appfiles" * "*" get) map
    (x => x -> ("appfiles/" + x.getName))