import org.typelevel.scalacoptions.ScalacOptions

name         := "signalement-api"
organization := "fr.gouv.beta"

version := "1.3.13"

scalaVersion := "2.13.14"

lazy val `signalement-api` = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  ws,
  ehcache,
  compilerPlugin(scalafixSemanticdb)
) ++ Dependencies.AppDependencies

scalafmtOnCompile := true

javacOptions ++= Seq(
  "--release",
  "21"
)
scalacOptions ++= Seq(
  "-explaintypes",
  "-Ywarn-macros:after",
  "-Wconf:cat=unused-imports&src=views/.*:s",
  "-Wconf:cat=unused:info",
  s"-Wconf:src=${target.value}/.*:s",
  "-Yrangepos"
//  "-quickfix:any" // should we enable it by default to replace scalafix rule ExplicitResultTypes ? No because we need to disable this rule sometimes
)

routesImport ++= Seq(
  "models.UserRole",
  "models.website.IdentificationStatus",
  "models.report.ReportFileOrigin",
  "java.time.OffsetDateTime",
  "models.investigation.InvestigationStatus",
  "models.website.WebsiteId",
  "models.engagement.EngagementId",
  "utils.SIRET",
  "models.report.reportfile.ReportFileId",
  "models.report.ReportResponseType",
  "models.report.delete.ReportAdminActionType",
  "models.PublicStat",
  "controllers.IdentificationStatusQueryStringBindable",
  "controllers.WebsiteIdPathBindable",
  "controllers.UUIDPathBindable",
  "controllers.OffsetDateTimeQueryStringBindable",
  "controllers.SIRETPathBindable",
  "controllers.ReportFileIdPathBindable",
  "controllers.ReportResponseTypeQueryStringBindable",
  "controllers.ReportAdminActionTypeQueryStringBindable",
  "controllers.ReportFileOriginQueryStringBindable",
  "controllers.PublicStatQueryStringBindable"
)

semanticdbVersion := scalafixSemanticdb.revision
scalafixOnCompile := true

Test / tpolecatExcludeOptions += ScalacOptions.warnNonUnitStatement
Test / tpolecatExcludeOptions += ScalacOptions.lintMissingInterpolator

resolvers += "Atlassian Releases" at "https://packages.atlassian.com/maven-public/"

Universal / mappings ++=
  (baseDirectory.value / "appfiles" * "*" get) map
    (x => x -> ("appfiles/" + x.getName))

Test / javaOptions += "-Dconfig.resource=test.application.conf"
javaOptions += "-Dpekko.http.parsing.max-uri-length=16k"

javaOptions += s"-Dtextlogs=${sys.env.getOrElse("USE_TEXT_LOGS", "false")}"

ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
