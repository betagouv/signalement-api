package controllers

import actors.ReportsExtractActor
import actors.ReportsZipExtractActor
import authentication.Authenticator
import cats.implicits.catsSyntaxOption
import controllers.error.AppError.MalformedQueryParams
import models._
import models.report.ReportFilterApi
import models.report.ReportFilterProApi
import models.report.ReportSort
import orchestrators.ReportOrchestrator
import org.apache.pekko.actor.typed
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.asyncfiles.AsyncFileRepositoryInterface
import utils.QueryStringMapper

import java.time.ZoneId
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class ReportListController(
    reportOrchestrator: ReportOrchestrator,
    asyncFileRepository: AsyncFileRepositoryInterface,
    reportsExtractActor: typed.ActorRef[ReportsExtractActor.ReportsExtractCommand],
    reportsZipExtractActor: typed.ActorRef[ReportsZipExtractActor.ReportsExtractCommand],
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  implicit val timeout: org.apache.pekko.util.Timeout = 5.seconds
  val logger: Logger                                  = Logger(this.getClass)

  def searchReports() = Act.secured.all.allowImpersonation.async { implicit request =>
    implicit val userRole: Option[UserRole] = Some(request.identity.userRole)

    val reportFilters = request.identity.userRole match {
      case UserRole.Professionnel =>
        ReportFilterProApi.fromQueryString(request.queryString).map(ReportFilterProApi.toReportFilter)
      case _ => ReportFilterApi.fromQueryString(request.queryString).map(ReportFilterApi.toReportFilter)
    }

    val reportSort = request.identity.userRole match {
      case UserRole.Professionnel => ReportSort.fromQueryString(request.queryString)
      case _                      => None
    }

    reportFilters
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
              sort = reportSort
            )
          } yield Ok(Json.toJson(paginatedReports))
      )
  }

  def createReportsSearchExcelExtract = Act.secured.all.allowImpersonation.async { implicit request =>
    for {
      reportFilter <- parseReportFilter(request.queryString, request.identity)
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

  def createReportsSearchZipExtract() =
    Act.secured.all.allowImpersonation.async(parse.empty) { implicit request =>
      for {
        reportFilter <- parseReportFilter(request.queryString, request.identity)
        _ = logger.debug(s"Requesting report for user ${request.identity.email}")
        file <- asyncFileRepository
          .create(AsyncFile.build(request.identity, kind = AsyncFileKind.ReportsZip))
        _ = reportsZipExtractActor ! ReportsZipExtractActor.ExtractRequest(file.id, request.identity, reportFilter)
      } yield Ok
    }

  private def parseReportFilter(queryString: Map[String, Seq[String]], identity: User) = {

    val reportFilters = identity.userRole match {
      case UserRole.Professionnel =>
        ReportFilterProApi.fromQueryString(queryString).map(ReportFilterProApi.toReportFilter)
      case _ => ReportFilterApi.fromQueryString(queryString).map(ReportFilterApi.toReportFilter)
    }
    reportFilters.toOption
      .liftTo[Future] {
        logger.warn(s"Failed to parse ReportFilter query params")
        throw MalformedQueryParams
      }
  }

}
