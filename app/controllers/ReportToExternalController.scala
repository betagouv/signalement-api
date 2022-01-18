package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models._
import play.api.Logger
import play.api.libs.json.Json
import repositories._
import utils.QueryStringMapper
import utils.silhouette.api.APIKeyEnv

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class ReportToExternalController @Inject() (
    reportRepository: ReportRepository,
    val silhouette: Silhouette[APIKeyEnv]
)(implicit val ec: ExecutionContext)
    extends ApiKeyBaseController {

  val logger: Logger = Logger(this.getClass)

  def getReportToExternal(uuid: String) = SecuredAction.async { _ =>
    Try(UUID.fromString(uuid)) match {
      case Failure(_) => Future.successful(PreconditionFailed)
      case Success(id) =>
        for {
          report <- reportRepository.getReport(id)
          reportFiles <- report.map(r => reportRepository.retrieveReportFiles(r.id)).getOrElse(Future(List.empty))
        } yield report
          .map(report => ReportWithFiles(report, reportFiles.filter(_.origin == ReportFileOrigin.CONSUMER)))
          .map(ReportWithFilesToExternal.fromReportWithFiles)
          .map(report => Ok(Json.toJson(report)))
          .getOrElse(NotFound)
    }
  }

  def searchReportsToExternal() = silhouetteAPIKey.SecuredAction.async { implicit request =>
    val qs = new QueryStringMapper(request.queryString)
    val filter = ReportFilter(
      siretSirenList = qs.string("siret").map(List(_)).getOrElse(List()),
      start = qs.localDate("start"),
      end = qs.localDate("end")
    )
    for {
      reports <- reportRepository.getReports(filter, Some(0), Some(1000000))
    } yield Ok(Json.toJson(reports.entities.map(ReportToExternal.fromReport)))
  }
}
