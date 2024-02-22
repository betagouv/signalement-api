import play.sbt.PlayImport.specs2

import sbt._

object Dependencies {
  object Versions {

    // Cannot be updated to "5.1.0" compatibility issues with current version of alpakka slick (using slick 3.3.3, next is 3.4.1)
    lazy val playSlickVersion = "5.0.2"
    // Cannot be updated to "0.21.1" compatibility issues with current version of alpakka slick (using slick 3.3.3, next is 3.4.1)
    lazy val slickPgVersion    = "0.20.4"
    lazy val playMailerVersion = "10.0.0"
    // Cannot be updated to "10.4.0+" (current is 10.5.2). Compatibility issues with play  2.8.20 (using akka 2.6.21)
    lazy val akkaHttpVersion = "10.2.10"
    // Cannot be updated to "5.0.0+" (current is 6.0.2). Compatibility issues with play 2.8.20 (still using akka 2.6.21)
    lazy val alpakkaVersion = "4.0.0"
    // Cannot be updated to "1.8.0" (it's based on play 3.0.0 / Pekko)
    lazy val enumeratumVersion         = "1.7.3"
    lazy val sentryVersion             = "6.34.0"
    lazy val jbcrypt                   = "0.4"
    lazy val specs2MatcherExtraVersion = "4.20.3"
    lazy val scalaCheckVersion         = "1.17.0"
    lazy val catsCoreVersion           = "2.10.0"
    lazy val pureConfigVersion         = "0.17.4"
    lazy val playJsonExtensionsVersion = "0.42.0"
    lazy val awsJavaSdkS3Version       = "1.12.603"
    lazy val jacksonModuleScalaVersion = "2.16.0"
    lazy val postgresqlVersion         = "42.5.5"
    lazy val refinedVersion            = "0.11.0"
    lazy val spoiwoVersion             = "2.2.1"
    lazy val itext7CoreVersion         = "8.0.2"
    lazy val html2pdfVersion           = "5.0.2"
    lazy val chimneyVersion            = "0.8.3"
    lazy val sttp                      = "3.9.1"
    lazy val sttpPlayJson              = "3.9.1"
    lazy val flyWayVersion             = "10.0.1"
    lazy val janino                    = "3.1.11"
    // Cannot be updated to "7.4" because of the following error when logging as JSON:
    // java.lang.NoSuchMethodError: 'java.time.Instant ch.qos.logback.classic.spi.ILoggingEvent.getInstant()'
    // If we want to upgrade, we MUST check json logs (env var USE_TEXT_LOGS set to false) to see if this error still happen
    lazy val logstashLogbackEncoder = "7.3"

  }

  object Test {
    val specs2Import       = specs2            % "test"
    val specs2MatcherExtra = "org.specs2"     %% "specs2-matcher-extra" % Versions.specs2MatcherExtraVersion % "test"
    val scalaCheck         = "org.scalacheck" %% "scalacheck"           % Versions.scalaCheckVersion         % "test"
    val akkaTestKit = "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.21" % "test"

  }

  object Compile {
    val flywayCore     = "org.flywaydb" % "flyway-core"                % Versions.flyWayVersion
    val flywayPostgres = "org.flywaydb" % "flyway-database-postgresql" % Versions.flyWayVersion
    val janino = "org.codehaus.janino" % "janino" % Versions.janino // Needed for the <if> in logback conf
    val commonsCompiler = "org.codehaus.janino" % "commons-compiler" % Versions.janino // Needed for janino
    val logstashLogBackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % Versions.logstashLogbackEncoder
    val sttpPlayJson = "com.softwaremill.sttp.client3" %% "play-json"                        % Versions.sttpPlayJson
    val sttp         = "com.softwaremill.sttp.client3" %% "core"                             % Versions.sttp
    val asyncSttp         = "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % Versions.sttp
    val sentry       = "io.sentry"                      % "sentry-logback"                   % Versions.sentryVersion
    val catsCore     = "org.typelevel"                 %% "cats-core"                        % Versions.catsCoreVersion
    val pureConfig         = "com.github.pureconfig" %% "pureconfig"                % Versions.pureConfigVersion
    val playJsonExtensions = "ai.x"                  %% "play-json-extensions"      % Versions.playJsonExtensionsVersion
    val playSlick          = "com.typesafe.play"     %% "play-slick"                % Versions.playSlickVersion
    val slickPg            = "com.github.tminglei"   %% "slick-pg"                  % Versions.slickPgVersion
    val slickPgPlayJson    = "com.github.tminglei"   %% "slick-pg_play-json"        % Versions.slickPgVersion
    val alpakkaSlick       = "com.lightbend.akka"    %% "akka-stream-alpakka-slick" % Versions.alpakkaVersion
    val playMailer         = "org.playframework"     %% "play-mailer"               % Versions.playMailerVersion
    val alpakkaS3          = "com.lightbend.akka"    %% "akka-stream-alpakka-s3"    % Versions.alpakkaVersion
    val alpakkaCSV         = "com.lightbend.akka"    %% "akka-stream-alpakka-csv"   % Versions.alpakkaVersion
    val alpakkaFile        = "com.lightbend.akka"    %% "akka-stream-alpakka-file"  % Versions.alpakkaVersion
    val akkaHttp           = "com.typesafe.akka"     %% "akka-http"                 % Versions.akkaHttpVersion
    val akkaHttpXml        = "com.typesafe.akka"     %% "akka-http-xml"             % Versions.akkaHttpVersion
    val jbcrypt            = "org.mindrot"            % "jbcrypt"                   % "0.4"
    val enumeratum         = "com.beachape"          %% "enumeratum"                % Versions.enumeratumVersion
    val enumeratumPlay     = "com.beachape"          %% "enumeratum-play"           % Versions.enumeratumVersion
    val awsJavaSdkS3       = "com.amazonaws"          % "aws-java-sdk-s3"           % Versions.awsJavaSdkS3Version
    val jacksonModuleScala =
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % Versions.jacksonModuleScalaVersion
    val postgresql = "org.postgresql" % "postgresql"  % Versions.postgresqlVersion
    val refinded   = "eu.timepit"    %% "refined"     % Versions.refinedVersion
    val spoiwo     = "com.norbitltd" %% "spoiwo"      % Versions.spoiwoVersion
    val itext7Core = "com.itextpdf"   % "itext7-core" % Versions.itext7CoreVersion
    val html2pdf   = "com.itextpdf"   % "html2pdf"    % Versions.html2pdfVersion
    val chimney    = "io.scalaland"  %% "chimney"     % Versions.chimneyVersion

  }

  val AppDependencies = Seq(
    Compile.janino,
    Compile.commonsCompiler,
    Compile.logstashLogBackEncoder,
    Compile.sttp,
    Compile.asyncSttp,
    Compile.sttpPlayJson,
    Compile.sentry,
    Compile.catsCore,
    Compile.pureConfig,
    Compile.playJsonExtensions,
    Compile.playSlick,
    Compile.slickPg,
    Compile.slickPgPlayJson,
    Compile.alpakkaSlick,
    Compile.playMailer,
    Compile.alpakkaS3,
    Compile.alpakkaCSV,
    Compile.alpakkaFile,
    Compile.akkaHttp,
    Compile.akkaHttpXml,
    Compile.jbcrypt,
    Compile.enumeratum,
    Compile.enumeratumPlay,
    Compile.awsJavaSdkS3,
    Compile.jacksonModuleScala,
    Compile.postgresql,
    Compile.refinded,
    Compile.spoiwo,
    Compile.itext7Core,
    Compile.html2pdf,
    Compile.chimney,
    Compile.flywayCore,
    Compile.flywayPostgres,
    Test.specs2Import,
    Test.specs2MatcherExtra,
    Test.scalaCheck,
    Test.akkaTestKit
  )
}
