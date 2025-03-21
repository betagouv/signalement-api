package controllers

import authentication.Authenticator
import controllers.error.AppError.MalformedQueryParams
import models._
import models.report.ReportFilter.transmittedReportsFilter
import models.report.{ReportFilter, ReportFilterApi, ReportResponseType, ReportStatus, ReportsCountBySubcategoriesFilter}
import models.report.ReportStatus.statusWithProResponse
import models.report.delete.ReportAdminActionType
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.CompanyOrchestrator
import orchestrators.StatsOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.mvc.Result
import play.api.mvc.Results
import utils.QueryStringMapper

import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

class StatisticController(
    val companyOrchestrator: CompanyOrchestrator,
    statsOrchestrator: StatsOrchestrator,
    val companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseCompanyController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def getReportsCount() = Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { request =>
    ReportFilterApi
      .fromQueryString(request.queryString)
      .map(ReportFilterApi.toReportFilter)
      .fold(
        error => {
          logger.error("Cannot parse querystring", error)
          Future.successful(BadRequest)
        },
        filters =>
          statsOrchestrator
            .getReportCount(Some(request.identity), filters)
            .map(count => Ok(Json.obj("value" -> count)))
      )
  }

  def getReportsCountCurve() = Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { request =>
    getReportsCountCurveFromQueryString(
      request.queryString,
      request.identity
    )
  }

  def getReportsCountCurveForCompany(companyId: UUID) =
    Act.securedWithCompanyAccessById(companyId).allowImpersonation.async { request =>
      getReportsCountCurveFromQueryString(
        request.queryString,
        request.identity,
        reportFilterModification = _.copy(companyIds = Seq(companyId))
      )
    }

  private def getReportsCountCurveFromQueryString(
      queryString: Map[String, Seq[String]],
      user: User,
      reportFilterModification: ReportFilter => ReportFilter = identity
  ): Future[Result] =
    ReportFilterApi
      .fromQueryString(queryString)
      .map(ReportFilterApi.toReportFilter) match {
      case Success(reportFilter) =>
        val modifiedReportFilter = reportFilterModification(reportFilter)
        val mapper               = new QueryStringMapper(queryString)
        val ticks                = mapper.int("ticks").getOrElse(12)
        val tickDuration = mapper
          .string("tickDuration")
          .flatMap(CurveTickDuration.namesToValuesMap.get)
          .getOrElse(CurveTickDuration.Month)
        for {
          curve <- statsOrchestrator
            .getReportsCountCurve(Some(user), modifiedReportFilter, ticks, tickDuration)
        } yield Ok(Json.toJson(curve))
      case Failure(error) =>
        logger.error("Cannot parse querystring", error)
        Future.successful(BadRequest)

    }

  def getDelayReportResponseInHours(companyId: UUID) =
    Act.securedWithCompanyAccessById(companyId).allowImpersonation.async { request =>
      statsOrchestrator
        .getResponseAvgDelay(companyId, request.identity.userRole)
        .map(count => Ok(Json.toJson(StatsValue(count.map(_.toHours.toInt)))))
    }

  def getReportResponseReviews(companyId: UUID) = Act.securedWithCompanyAccessById(companyId).allowImpersonation.async {
    statsOrchestrator.getReportResponseReview(Some(companyId)).map(x => Ok(Json.toJson(x)))
  }

  def getReportEngagementReviews(companyId: UUID) =
    Act.securedWithCompanyAccessById(companyId).allowImpersonation.async {
      statsOrchestrator.getReportEngagementReview(Some(companyId)).map(x => Ok(Json.toJson(x)))
    }

  def getReportsTagsDistribution(companyId: Option[UUID]) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { request =>
      statsOrchestrator.getReportsTagsDistribution(companyId, request.identity).map(x => Ok(Json.toJson(x)))
    }

  def getReportsStatusDistribution(companyId: UUID) =
    Act.securedWithCompanyAccessById(companyId).allowImpersonation.async { request =>
      statsOrchestrator.getReportsStatusDistribution(Some(companyId), request.identity).map(x => Ok(Json.toJson(x)))
    }

  def getAcceptedResponsesDistribution(companyId: UUID) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { request =>
      statsOrchestrator
        .getAcceptedResponsesDistribution(companyId, request.identity)
        .map(x => Ok(Json.toJson(x)))
    }

  def getProReportToTransmitStat() =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { request =>
      // Includes the reports that we want to transmit to a pro
      // but we have not identified the company
      val filter = ReportFilter(
        visibleToPro = Some(true)
      )
      statsOrchestrator
        .getReportsCountCurve(Some(request.identity), filter)
        .map(curve => Ok(Json.toJson(curve)))
    }

  def getProReportTransmittedStat() = Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { request =>
    statsOrchestrator
      .getReportsCountCurve(Some(request.identity), transmittedReportsFilter)
      .map(curve => Ok(Json.toJson(curve)))
  }

  def getProReportResponseStat(responseTypeQuery: Option[List[ReportResponseType]]) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async(parse.empty) { request =>
      val statusFilter = responseTypeQuery
        .filter(_.nonEmpty)
        .map(_.map(ReportStatus.fromResponseType))
        .getOrElse(statusWithProResponse)
      val filter = ReportFilter(status = statusFilter)
      statsOrchestrator
        .getReportsCountCurve(Some(request.identity), filter)
        .map(curve => Ok(Json.toJson(curve)))
    }

  def dgccrfAccountsCurve(ticks: Option[Int]) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async(parse.empty) { _ =>
      statsOrchestrator.dgccrfAccountsCurve(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
    }

  def dgccrfActiveAccountsCurve(ticks: Option[Int]) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async(parse.empty) { _ =>
      statsOrchestrator.dgccrfActiveAccountsCurve(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
    }

  def dgccrfSubscription(ticks: Option[Int]) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async(parse.empty) { _ =>
      statsOrchestrator.dgccrfSubscription(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
    }

  def dgccrfControlsCurve(ticks: Option[Int]) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async(parse.empty) { _ =>
      statsOrchestrator.dgccrfControlsCurve(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
    }

  def getReportsCountByDepartments() =
    Act.secured.adminsAndReadonlyAndDgccrf.allowImpersonation.async { implicit request =>
      val mapper = new QueryStringMapper(request.queryString)
      val start  = mapper.timeWithLocalDateRetrocompatStartOfDay("start")
      val end    = mapper.timeWithLocalDateRetrocompatEndOfDay("end")
      statsOrchestrator.countByDepartments(start, end).map(res => Ok(Json.toJson(res)))
    }

  def reportsCountBySubcategories() =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { implicit request =>
      ReportsCountBySubcategoriesFilter.fromQueryString(request.queryString) match {
        case Failure(error) =>
          logger.error("Cannot parse querystring" + request.queryString, error)
          Future.failed(MalformedQueryParams)
        case Success(filters) =>
          statsOrchestrator
            .reportsCountBySubcategories(request.identity, filters)
            .map(res => Ok(Json.toJson(res)))
      }
    }

  def downloadReportsCountBySubcategories(lang: String) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { implicit request =>
      ReportsCountBySubcategoriesFilter.fromQueryString(request.queryString) match {
        case Failure(error) =>
          logger.error("Cannot parse querystring" + request.queryString, error)
          Future.failed(MalformedQueryParams)
        case Success(filters) =>
          val fileName = s"subcategories_${OffsetDateTime.now()}.csv"
          statsOrchestrator
            .downloadReportsCountBySubcategories(request.identity, filters, Locale.forLanguageTag(lang))
            .map(res => Ok(res).withHeaders(Results.contentDispositionHeader(inline = false, Some(fileName)).toSeq: _*))
      }
    }

  def fetchAdminActionEvents(companyId: UUID, reportAdminActionType: ReportAdminActionType) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { _ =>
      statsOrchestrator
        .fetchAdminActionEvents(companyId, reportAdminActionType)
        .map(count => Ok(Json.obj("value" -> count)))
    }

}
