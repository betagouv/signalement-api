name := "signalement-api"
organization := "fr.gouv.beta"

version := "0.1"

scalaVersion := "2.12.7"

lazy val `signalement-api` = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  guice
)