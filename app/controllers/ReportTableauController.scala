package controllers

import com.mohiva.play.silhouette.api.Silhouette
import play.api.Logger
import play.api.libs.json.Json
import repositories.TableauReportRepository
import utils.silhouette.api.APIKeyEnv

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ReportTableauController @Inject() (
    reportRepository: TableauReportRepository,
    val silhouette: Silhouette[APIKeyEnv]
)(implicit val ec: ExecutionContext)
    extends ApiKeyBaseController {

  val logger: Logger = Logger(this.getClass)

  def getReport() = SecuredAction.async { _ =>
    logger.info("Getting reports for tableau")
    reportRepository.reports().map(report => Ok(Json.toJson(report)))
  }

}
