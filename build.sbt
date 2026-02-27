import org.typelevel.scalacoptions.ScalacOptions

name         := "signalement-api"
organization := "fr.gouv.beta"

version := "1.3.13"

scalaVersion := "2.13.16"

lazy val `signalement-api` = (project in file(".")).enablePlugins(PlayScala).enablePlugins(JavaAppPackaging)

libraryDependencies ++= Seq(
  ws,
  ehcache,
  compilerPlugin(scalafixSemanticdb)
) ++ Dependencies.AppDependencies

//scalafmtOnCompile := true
scalacOptions ++= Seq(
  "-explaintypes",
  "-Ywarn-macros:after",
  "-release:17",
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
  "controllers.IdentificationStatusQueryStringBindable",
  "controllers.WebsiteIdPathBindable",
  "controllers.UUIDPathBindable",
  "controllers.OffsetDateTimeQueryStringBindable",
  "controllers.SIRETPathBindable",
  "controllers.ReportFileIdPathBindable",
  "controllers.ReportResponseTypeQueryStringBindable",
  "controllers.ReportAdminActionTypeQueryStringBindable",
  "controllers.ReportFileOriginQueryStringBindable"
)

semanticdbVersion := scalafixSemanticdb.revision
scalafixOnCompile := true

Test / tpolecatExcludeOptions += ScalacOptions.warnNonUnitStatement
Test / tpolecatExcludeOptions += ScalacOptions.lintMissingInterpolator

resolvers += "Atlassian Releases" at "https://packages.atlassian.com/maven-public/"

def doLint(cmd: String) = s"scalafmt;$cmd;"

addCommandAlias("lint", doLint("scalafmt;compile;scalafix"))
addCommandAlias("lintTest", doLint("scalafmtAll;Test / compile;scalafixAll"))

Universal / mappings ++=
  (baseDirectory.value / "appfiles" * "*" get) map
    (x => x -> ("appfiles/" + x.getName))

Test / javaOptions += "-Dconfig.resource=test.application.conf"
javaOptions += "-Dpekko.http.parsing.max-uri-length=16k"

javaOptions += s"-Dtextlogs=${sys.env.getOrElse("USE_TEXT_LOGS", "false")}"

ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

Universal / javaOptions ++= {
  sys.env.get("ENV") match {
    case _ => Seq(
      "-J-Xms1356m",
      "-J-Xmx2048m",
      "-J-XX:MaxMetaspaceSize=256m",
      "-J-XX:MaxGCPauseMillis=200",

      // Safe: caps runaway direct memory, root cause fix
      "-J-XX:MaxDirectMemorySize=512m",

      // Safe: disables heap arenas which are unused anyway
      "-J-Dio.netty.allocator.numHeapArenas=0",

      // Safe: GC visibility you currently lack
      "-J-Xlog:gc*=info:file=/tmp/gc-app.log:time,uptime,level,tags:filecount=5,filesize=20m"
    )
  }
}
