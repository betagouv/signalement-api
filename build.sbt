name := "signalement-api"
organization := "fr.gouv.beta"

version := "1.3.13"

scalaVersion := "2.13.10"

lazy val `signalement-api` = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  ws,
  ehcache,
  compilerPlugin(scalafixSemanticdb)
) ++ Dependencies.AppDependencies

scalafmtOnCompile := true
scalacOptions ++= Seq(
  "-explaintypes",
  "-Ywarn-macros:after",
  "-release:17",
  "-Wconf:cat=unused-imports&src=views/.*:s",
  "-Wconf:cat=unused:info",
  s"-Wconf:src=${target.value}/.*:s",
  "-Yrangepos"
)

routesImport ++= Seq(
  "models.website.IdentificationStatus",
  "java.time.OffsetDateTime",
  "models.investigation.Practice",
  "models.investigation.DepartmentDivision",
  "models.investigation.InvestigationStatus",
  "models.website.WebsiteId",
  "utils.SIRET",
  "models.report.reportfile.ReportFileId",
  "models.report.ReportResponseType",
  "models.PublicStat",
  "controllers.IdentificationStatusQueryStringBindable",
  "controllers.WebsiteIdPathBindable",
  "controllers.UUIDPathBindable",
  "controllers.OffsetDateTimeQueryStringBindable",
  "controllers.SIRETPathBindable",
  "controllers.ReportFileIdPathBindable",
  "controllers.ReportResponseTypeQueryStringBindable",
  "controllers.PublicStatQueryStringBindable"
)

semanticdbVersion := scalafixSemanticdb.revision
scalafixOnCompile := true

resolvers += "Atlassian Releases" at "https://packages.atlassian.com/maven-public/"

Universal / mappings ++=
  (baseDirectory.value / "appfiles" * "*" get) map
    (x => x -> ("appfiles/" + x.getName))

Test / javaOptions += "-Dconfig.resource=test.application.conf"
javaOptions += "-Dakka.http.parsing.max-uri-length=16k"

javaOptions += s"-Dtextlogs=${sys.env.getOrElse("USE_TEXT_LOGS", "false")}"
