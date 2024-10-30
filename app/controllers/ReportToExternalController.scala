package controllers

import authentication.Authenticator
import config.SignalConsoConfiguration
import models.Consumer
import models.PaginatedResult.paginatedResultWrites
import models.report._
import models.report.ReportWithFilesToExternal.format
import models.report.reportmetadata.ReportWithMetadataAndBookmark
import orchestrators.ReportOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.report.ReportRepositoryInterface
import repositories.reportfile.ReportFileRepositoryInterface
import utils.QueryStringMapper

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class ReportToExternalController(
    reportRepository: ReportRepositoryInterface,
    reportFileRepository: ReportFileRepositoryInterface,
    reportOrchestrator: ReportOrchestrator,
    authenticator: Authenticator[Consumer],
    signalConsoConfiguration: SignalConsoConfiguration,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends ApiKeyBaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def getReportToExternal(uuid: String) = SecuredAction.async { _ =>
    logger.debug("Calling report to external")
    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) =>
        for {
          report <- reportRepository.get(id)
          reportFiles <- report
            .map(r => reportFileRepository.retrieveReportFiles(r.id))
            .getOrElse(Future.successful(List.empty))
        } yield report
          .map(report =>
            ReportWithFilesToExternal.fromReportAndFiles(
              report = report,
              reportFiles = reportFiles.filter(_.origin == ReportFileOrigin.Consumer)
            )
          )
          .map(r => Ok(Json.toJson(r)))
          .getOrElse(NotFound)
    }
  }

  def searchReportsToExternal() = SecuredAction.async { implicit request =>
    val qs = new QueryStringMapper(request.queryString)
    val filter = ReportFilter(
      siretSirenList = qs.string("siret").map(List(_)).getOrElse(List()),
      start = qs.timeWithLocalDateRetrocompatStartOfDay("start"),
      end = qs.timeWithLocalDateRetrocompatEndOfDay("end"),
      withTags = qs.seq("tags").map(ReportTag.withName)
    )

    for {
      reportsWithFiles <- reportRepository.getReportsWithFiles(
        None,
        filter = filter
      )
    } yield Ok(
      Json.toJson(
        reportsWithFiles.map { case (report, fileList) =>
          ReportWithFilesToExternal(
            ReportToExternal.fromReport(report),
            fileList.map(ReportFileToExternal.fromReportFile)
          )
        }
      )
    )
  }

  def searchReportsToExternalV2() = SecuredAction.async { implicit request =>
    val qs = new QueryStringMapper(request.queryString)
    val filter = ReportFilter(
      siretSirenList = qs.string("siret").map(List(_)).getOrElse(List()),
      start = qs.timeWithLocalDateRetrocompatStartOfDay("start"),
      end = qs.timeWithLocalDateRetrocompatEndOfDay("end"),
      withTags = qs.seq("tags").map(ReportTag.withName)
    )
    val offset = qs.long("offset")
    val limit  = qs.int("limit")

    for {
      reportsWithFiles <- reportOrchestrator.getReportsWithFile[ReportWithFilesToExternal](
        None,
        filter = filter,
        offset,
        limit,
        signalConsoConfiguration.reportsListLimitMax,
        (r: ReportWithMetadataAndBookmark, m: Map[UUID, List[ReportFile]]) =>
          ReportWithFilesToExternal(
            ReportToExternal.fromReport(r.report),
            m.getOrElse(r.report.id, Nil).map(ReportFileToExternal.fromReportFile)
          )
      )
    } yield Ok(
      Json.toJson(reportsWithFiles)(paginatedResultWrites[ReportWithFilesToExternal](ReportWithFilesToExternal.format))
    )
  }

  /** @deprecated
    *   Keep it for retro-compatibility purpose but searchReportsToExternal() is the good one.
    */
  def searchReportsToExternalBySiret(siret: String) = SecuredAction.async { implicit request =>
    val qs = new QueryStringMapper(request.queryString)
    val filter = ReportFilter(
      siretSirenList = List(siret),
      start = qs.timeWithLocalDateRetrocompatStartOfDay("start"),
      end = qs.timeWithLocalDateRetrocompatEndOfDay("end")
    )
    for {
      reportsWithMetadata <- reportRepository.getReports(None, filter, Some(0), Some(1000000))
      reports         = reportsWithMetadata.entities.map(_.report)
      reportsExternal = reports.map(ReportToExternal.fromReport)
    } yield Ok(Json.toJson(reportsExternal))
  }

}
