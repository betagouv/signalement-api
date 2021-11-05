package controllers

import actors.ReportsExtractActor
import akka.actor.ActorRef
import akka.pattern.ask
import com.mohiva.play.silhouette.api.Silhouette
import models._
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.ReportOrchestrator
import play.api.Logger
import play.api.libs.json.JsError
import play.api.libs.json.Json
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission

import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

@Singleton
class ReportListController @Inject() (
    reportOrchestrator: ReportOrchestrator,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    @Named("reports-extract-actor") reportsExtractActor: ActorRef,
    val silhouette: Silhouette[AuthEnv],
    val silhouetteAPIKey: Silhouette[APIKeyEnv]
)(implicit val executionContext: ExecutionContext)
    extends BaseController {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger = Logger(this.getClass)

  def getReports(offset: Option[Long], limit: Option[Int]) = SecuredAction.async(parse.json) { implicit request =>
    request.body
      .validate[ReportFilterBody]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        filter =>
          for {
            sanitizedSirenSirets <- companiesVisibilityOrchestrator.filterUnauthorizedSiretSirenList(
              filter.siretSirenList,
              request.identity
            )
            paginatedReports <- reportOrchestrator.getReportsForUser(
              connectedUser = request.identity,
              filter = filter.copy(siretSirenList = sanitizedSirenSirets).toReportFilter(request.identity.userRole),
              offset = offset,
              limit = limit
            )
          } yield Ok(Json.toJson(paginatedReports))
      )
  }

  def extractReports = SecuredAction(WithPermission(UserPermission.listReports)).async(parse.json) { implicit request =>
    request.body
      .validate[ReportFilterBody]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        filters =>
          for {
            sanitizedSirenSirets <- companiesVisibilityOrchestrator.filterUnauthorizedSiretSirenList(
              filters.siretSirenList,
              request.identity
            )
          } yield {
            logger.debug(s"Requesting report for user ${request.identity.email}")
            reportsExtractActor ? ReportsExtractActor.ExtractRequest(
              request.identity,
              filters.copy(siretSirenList = sanitizedSirenSirets).toReportFilter(request.identity.userRole)
            )
            Ok
          }
      )
  }
}

object ReportListObjects {

  case class ReportList(reportIds: List[UUID])

}
