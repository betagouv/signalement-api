package controllers

import actors.ReportsExtractActor
import akka.actor.ActorRef
import akka.pattern.ask
import com.mohiva.play.silhouette.api.Silhouette
import models._
import models.report.ReportFilter
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.ReportOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class ReportListController @Inject() (
    reportOrchestrator: ReportOrchestrator,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    @Named("reports-extract-actor") reportsExtractActor: ActorRef,
    val silhouette: Silhouette[AuthEnv],
    val silhouetteAPIKey: Silhouette[APIKeyEnv]
)(implicit val ec: ExecutionContext)
    extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger = Logger(this.getClass)

  def getReports() = SecuredAction.async { implicit request =>
    ReportFilter
      .fromQueryString(request.queryString, request.identity.userRole)
      .flatMap(filters => PaginatedSearch.fromQueryString(request.queryString).map((filters, _)))
      .fold(
        error => {
          logger.error("Cannot parse querystring" + request.queryString, error)
          Future.successful(BadRequest)
        },
        filters =>
          for {
            paginatedReports <- reportOrchestrator.getReportsForUser(
              connectedUser = request.identity,
              filter = filters._1,
              offset = filters._2.offset,
              limit = filters._2.limit
            )
          } yield Ok(Json.toJson(paginatedReports))
      )
  }

  def extractReports = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
    ReportFilter
      .fromQueryString(request.queryString, request.identity.userRole)
      .fold(
        error => {
          logger.error("Cannot parse querystring", error)
          Future.successful(BadRequest)
        },
        filters => {
          logger.debug(s"Requesting report for user ${request.identity.email}")
          reportsExtractActor ? ReportsExtractActor.ExtractRequest(request.identity, filters)
          Future(Ok)
        }
      )
  }
}
