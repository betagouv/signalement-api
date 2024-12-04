package controllers

import actors.ReportsExtractActor
import org.apache.pekko.actor.typed
import authentication.Authenticator
import controllers.error.AppError.MalformedQueryParams
import models._
import models.report.ReportFilter
import models.report.ReportSort
import models.report.SortOrder
import orchestrators.ReportOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.asyncfiles.AsyncFileRepositoryInterface
import cats.implicits.catsSyntaxOption

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import utils.QueryStringMapper

import java.time.ZoneId

class ReportListController(
    reportOrchestrator: ReportOrchestrator,
    asyncFileRepository: AsyncFileRepositoryInterface,
    reportsExtractActor: typed.ActorRef[ReportsExtractActor.ReportsExtractCommand],
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  implicit val timeout: org.apache.pekko.util.Timeout = 5.seconds
  val logger: Logger                                  = Logger(this.getClass)

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
              limit = filters._2.limit,
              sortBy = ReportSort.fromQueryString(request.queryString),
              orderBy = SortOrder.fromQueryString(request.queryString)
            )
          } yield Ok(Json.toJson(paginatedReports))
      )
  }

  def extractReports = SecuredAction.async { implicit request =>
    for {
      reportFilter <- ReportFilter
        .fromQueryString(request.queryString)
        .toOption
        .liftTo[Future] {
          logger.warn(s"Failed to parse ReportFilter query params")
          throw MalformedQueryParams
        }
      _ = logger.debug(s"Parsing zone query param")
      zone <- (new QueryStringMapper(request.queryString))
        .timeZone("zone")
        // temporary retrocompat, so we can mep the API safely
        .orElse(Some(ZoneId.of("Europe/Paris")))
        .liftTo[Future] {
          logger.warn(s"Failed to parse zone query param")
          throw MalformedQueryParams
        }
      _ = logger.debug(s"Requesting report for user ${request.identity.email}")
      file <- asyncFileRepository
        .create(AsyncFile.build(request.identity, kind = AsyncFileKind.Reports))
      _ = reportsExtractActor ! ReportsExtractActor.ExtractRequest(file.id, request.identity, reportFilter, zone)
    } yield Ok
  }
}
