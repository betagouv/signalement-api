// We should be able to remove this when play 2.9 is released
// https://github.com/playframework/playframework/issues/11461
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

addSbtPlugin("com.typesafe.play" % "sbt-plugin"                % "2.8.20")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"              % "2.5.2")
addSbtPlugin("ch.epfl.scala"     % "sbt-scalafix"              % "0.11.1")
addSbtPlugin("org.typelevel"     % "sbt-tpolecat"              % "0.5.0")
addSbtPlugin("com.github.cb372"  % "sbt-explicit-dependencies" % "0.3.1")
addSbtPlugin("com.timushev.sbt"  % "sbt-updates"               % "0.6.4")
