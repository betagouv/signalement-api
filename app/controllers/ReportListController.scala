package controllers

import actors.ReportsExtractActor
import akka.actor.ActorRef
import akka.pattern.ask
import com.mohiva.play.silhouette.api.Silhouette
import models._
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.ReportOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import utils.QueryStringMapper
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithPermission

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

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

  def getReports() = SecuredAction.async { implicit request =>
    ReportFilter.fromQueryString(request.queryString, request.identity.userRole) match {
      case Failure(error) =>
        logger.error("Cannot parse querystring " + error)
        Future.successful(BadRequest)
      case Success(filters) =>
        val mapper = new QueryStringMapper(request.queryString)
        val offset = mapper.long("offset")
        val limit = mapper.int("limit")
        for {
          sanitizedSirenSirets <- companiesVisibilityOrchestrator.filterUnauthorizedSiretSirenList(
            filters.siretSirenList,
            request.identity
          )
          paginatedReports <- reportOrchestrator.getReportsForUser(
            connectedUser = request.identity,
            filter = filters.copy(siretSirenList = sanitizedSirenSirets),
            offset = offset,
            limit = limit
          )
        } yield Ok(Json.toJson(paginatedReports))
    }
  }

  def extractReports = SecuredAction(WithPermission(UserPermission.listReports)).async { implicit request =>
    ReportFilter.fromQueryString(request.queryString, request.identity.userRole) match {
      case Failure(error) =>
        logger.error("Cannot parse querystring " + error)
        Future.successful(BadRequest)
      case Success(filters) =>
        for {
          sanitizedSirenSirets <- companiesVisibilityOrchestrator.filterUnauthorizedSiretSirenList(
            filters.siretSirenList,
            request.identity
          )
        } yield {
          logger.debug(s"Requesting report for user ${request.identity.email}")
          reportsExtractActor ? ReportsExtractActor.ExtractRequest(
            request.identity,
            filters.copy(siretSirenList = sanitizedSirenSirets)
          )
          Ok
        }
    }
  }
}
