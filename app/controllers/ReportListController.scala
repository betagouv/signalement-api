package controllers

import controllers.error.AppError.MalformedQueryParams
import models._
import models.report.ReportFilter
import orchestrators.ReportOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class ReportListController(
    reportOrchestrator: ReportOrchestrator,
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  implicit val timeout: akka.util.Timeout = 5.seconds
  val logger: Logger                      = Logger(this.getClass)

  def getReports() = SecuredAction.async { implicit request =>
    implicit val userRole: Option[UserRole] = Some(request.identity.userRole)
    ReportFilter
      .fromQueryString(request.queryString)
      .flatMap(filters => PaginatedSearch.fromQueryString(request.queryString).map((filters, _)))
      .fold(
        error => {
          logger.error("Cannot parse querystring" + request.queryString, error)
          Future.failed(MalformedQueryParams)
        },
        filters =>
          for {
            paginatedReports <- reportOrchestrator.getReportsWithResponsesForUser(
              connectedUser = request.identity,
              filter = filters._1,
              offset = filters._2.offset,
              limit = filters._2.limit
            )
          } yield Ok(Json.toJson(paginatedReports))
      )
  }

}
