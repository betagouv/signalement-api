import play.sbt.PlayImport.specs2

import sbt._

object Dependencies {
  object Versions {

    // Cannot be updated to "5.1.0" compatibility issues with current version of alpakka slick (using slick 3.3.3, next is 3.4.1)
    lazy val playSlickVersion = "5.0.2"
    // Cannot be updated to "0.21.1" compatibility issues with current version of alpakka slick (using slick 3.3.3, next is 3.4.1)
    lazy val slickPgVersion    = "0.20.4"
    lazy val playMailerVersion = "8.0.1"
    // Cannot be updated to "10.4.0+" (current is 10.5.2). Compatibility issues with play  2.8.20 (using akka 2.6.21)
    lazy val akkaHttpVersion = "10.2.10"
    // Cannot be updated to "5.0.0+" (current is 6.0.2). Compatibility issues with play 2.8.20 (still using akka 2.6.21)
    lazy val alpakkaVersion        = "4.0.0"
    lazy val enumeratumVersion     = "1.7.3"
    lazy val sentryVersion         = "6.30.0"
    lazy val playSilhouetteVersion = "7.0.0"
    // Cannot be updated to "4.20.2" compatibility issues with play spec2 2.8.20 (still using 4.8.3)
    lazy val specs2MatcherExtraVersion = "4.8.3"
    lazy val scalaCheckVersion         = "1.17.0"
    lazy val catsCoreVersion           = "2.10.0"
    lazy val pureConfigVersion         = "0.17.4"
    lazy val playJsonExtensionsVersion = "0.42.0"
    lazy val awsJavaSdkS3Version       = "1.12.565"
    lazy val jacksonModuleScalaVersion = "2.15.2"
    lazy val postgresqlVersion         = "42.5.4"
    lazy val refinedVersion            = "0.11.0"
    lazy val ficusVersion              = "1.5.2"
    lazy val spoiwoVersion             = "2.2.1"
    lazy val itext7CoreVersion         = "8.0.1"
    lazy val html2pdfVersion           = "5.0.1"
    lazy val chimneyVersion            = "0.7.5"
    lazy val sttp                      = "3.9.0"
    lazy val sttpPlayJson              = "3.9.0"
    lazy val flyWayVersion             = "9.22.2"
    lazy val janino                    = "3.1.10"
    lazy val logstashLogbackEncoder    = "7.4"

  }

  object Test {
    val specs2Import       = specs2            % "test"
    val specs2MatcherExtra = "org.specs2"     %% "specs2-matcher-extra"    % Versions.specs2MatcherExtraVersion % "test"
    val scalaCheck         = "org.scalacheck" %% "scalacheck"              % Versions.scalaCheckVersion         % "test"
    val playSilhouette     = "com.mohiva"     %% "play-silhouette-testkit" % Versions.playSilhouetteVersion     % "test"

  }

  object Compile {
    val flyWayVersion = "org.flywaydb"        % "flyway-core" % Versions.flyWayVersion
    val janino        = "org.codehaus.janino" % "janino"      % Versions.janino // Needed for the <if> in logback conf
    val commonsCompiler = "org.codehaus.janino" % "commons-compiler" % Versions.janino // Needed for janino
    val logstashLogBackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % Versions.logstashLogbackEncoder
    val sttpPlayJson = "com.softwaremill.sttp.client3" %% "play-json"      % Versions.sttpPlayJson
    val sttp         = "com.softwaremill.sttp.client3" %% "core"           % Versions.sttp
    val sentry       = "io.sentry"                      % "sentry-logback" % Versions.sentryVersion
    val catsCore     = "org.typelevel"                 %% "cats-core"      % Versions.catsCoreVersion
    val pureConfig   = "com.github.pureconfig"         %% "pureconfig"     % Versions.pureConfigVersion
    val playJsonExtensions = "ai.x"                %% "play-json-extensions"      % Versions.playJsonExtensionsVersion
    val playSlick          = "com.typesafe.play"   %% "play-slick"                % Versions.playSlickVersion
    val slickPg            = "com.github.tminglei" %% "slick-pg"                  % Versions.slickPgVersion
    val slickPgPlayJson    = "com.github.tminglei" %% "slick-pg_play-json"        % Versions.slickPgVersion
    val alpakkaSlick       = "com.lightbend.akka"  %% "akka-stream-alpakka-slick" % Versions.alpakkaVersion
    val playMailer         = "com.typesafe.play"   %% "play-mailer"               % Versions.playMailerVersion
    val alpakkaS3          = "com.lightbend.akka"  %% "akka-stream-alpakka-s3"    % Versions.alpakkaVersion
    val alpakkaCSV         = "com.lightbend.akka"  %% "akka-stream-alpakka-csv"   % Versions.alpakkaVersion
    val alpakkaFile        = "com.lightbend.akka"  %% "akka-stream-alpakka-file"  % Versions.alpakkaVersion
    val akkaHttp           = "com.typesafe.akka"   %% "akka-http"                 % Versions.akkaHttpVersion
    val akkaHttpXml        = "com.typesafe.akka"   %% "akka-http-xml"             % Versions.akkaHttpVersion
    val playSilhouette     = "com.mohiva"          %% "play-silhouette"           % Versions.playSilhouetteVersion
    val playSilhouettePasswordBcrypt =
      "com.mohiva" %% "play-silhouette-password-bcrypt" % Versions.playSilhouetteVersion
    val playSilhouettePersistence = "com.mohiva"   %% "play-silhouette-persistence" % Versions.playSilhouetteVersion
    val playSilhouetteCryptoJca   = "com.mohiva"   %% "play-silhouette-crypto-jca"  % Versions.playSilhouetteVersion
    val enumeratum                = "com.beachape" %% "enumeratum"                  % Versions.enumeratumVersion
    val enumeratumPlay            = "com.beachape" %% "enumeratum-play"             % Versions.enumeratumVersion
    val awsJavaSdkS3              = "com.amazonaws" % "aws-java-sdk-s3"             % Versions.awsJavaSdkS3Version
    val jacksonModuleScala =
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % Versions.jacksonModuleScalaVersion
    val postgresql = "org.postgresql" % "postgresql"  % Versions.postgresqlVersion
    val refinded   = "eu.timepit"    %% "refined"     % Versions.refinedVersion
    val ficus      = "com.iheart"    %% "ficus"       % Versions.ficusVersion
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
    Compile.playSilhouette,
    Compile.playSilhouettePasswordBcrypt,
    Compile.playSilhouettePersistence,
    Compile.playSilhouetteCryptoJca,
    Compile.enumeratum,
    Compile.enumeratumPlay,
    Compile.awsJavaSdkS3,
    Compile.jacksonModuleScala,
    Compile.postgresql,
    Compile.refinded,
    Compile.ficus,
    Compile.spoiwo,
    Compile.itext7Core,
    Compile.html2pdf,
    Compile.chimney,
    Compile.flyWayVersion,
    Test.specs2Import,
    Test.specs2MatcherExtra,
    Test.scalaCheck,
    Test.playSilhouette
  )
}
