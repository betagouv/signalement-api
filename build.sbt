name := "signalement-api"
organization := "fr.gouv.beta"

version := "0.1"

scalaVersion := "2.12.7"

lazy val `signalement-api` = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  guice,
  evolutions,

  "org.postgresql" % "postgresql" % "42.2.2",
  "com.typesafe.play" %% "play-slick" %  "3.0.2",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.2",
)