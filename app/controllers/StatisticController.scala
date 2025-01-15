package controllers

import authentication.Authenticator
import controllers.error.AppError.MalformedQueryParams
import models._
import models.report.ReportFilter.transmittedReportsFilter
import models.report.ReportFilter
import models.report.ReportResponseType
import models.report.ReportStatus
import models.report.ReportsCountBySubcategoriesFilter
import models.report.ReportStatus.statusWithProResponse
import models.report.delete.ReportAdminActionType
import orchestrators.StatsOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.mvc.Results
import utils.QueryStringMapper
import authentication.actions.UserAction.WithRole

import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

class StatisticController(
    statsOrchestrator: StatsOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def getReportsCount() = SecuredAction.async { request =>
    ReportFilter
      .fromQueryString(request.queryString)
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

  /** Nom de fonction adoubé par Saïd. En cas d'incompréhension, merci de le contacter directement
    */
  def getReportsCountCurve() = SecuredAction.async { request =>
    ReportFilter
      .fromQueryString(request.queryString)
      .fold(
        error => {
          logger.error("Cannot parse querystring", error)
          Future.successful(BadRequest)
        },
        filters => {
          val mapper = new QueryStringMapper(request.queryString)
          val ticks  = mapper.int("ticks").getOrElse(12)
          val tickDuration = mapper
            .string("tickDuration")
            .flatMap(CurveTickDuration.namesToValuesMap.get)
            .getOrElse(CurveTickDuration.Month)
          statsOrchestrator
            .getReportsCountCurve(Some(request.identity), filters, ticks, tickDuration)
            .map(curve => Ok(Json.toJson(curve)))
        }
      )
  }

  def getPublicStatCount(publicStat: PublicStat) = IpRateLimitedAction2.async {
    ((publicStat.filter, publicStat.percentageBaseFilter) match {
      case (filter, Some(percentageBaseFilter)) =>
        statsOrchestrator.getReportCountPercentageWithinReliableDates(None, filter, percentageBaseFilter)
      case (filter, _) =>
        statsOrchestrator.getReportCount(None, filter)
    }).map(curve => Ok(Json.toJson(curve)))
  }

  def getPublicStatCurve(publicStat: PublicStat) = IpRateLimitedAction2.async {
    ((publicStat.filter, publicStat.percentageBaseFilter) match {
      case (filter, Some(percentageBaseFilter)) =>
        statsOrchestrator.getReportsCountPercentageCurve(None, filter, percentageBaseFilter)
      case (filter, _) =>
        statsOrchestrator.getReportsCountCurve(None, filter)
    }).map(curve => Ok(Json.toJson(curve)))
  }

  def getDelayReportReadInHours(companyId: Option[UUID]) = SecuredAction
    .andThen(
      WithRole(UserRole.AdminsAndReadOnlyAndCCRF)
    )
    .async {
      statsOrchestrator
        .getReadAvgDelay(companyId)
        .map(count => Ok(Json.toJson(StatsValue(count.map(_.toHours.toInt)))))
    }

  def getDelayReportResponseInHours(companyId: Option[UUID]) = SecuredAction.async { request =>
    statsOrchestrator
      .getResponseAvgDelay(companyId: Option[UUID], request.identity.userRole)
      .map(count => Ok(Json.toJson(StatsValue(count.map(_.toHours.toInt)))))
  }

  def getReportResponseReviews(companyId: Option[UUID]) = SecuredAction.async {
    statsOrchestrator.getReportResponseReview(companyId).map(x => Ok(Json.toJson(x)))
  }

  def getReportEngagementReviews(companyId: Option[UUID]) = SecuredAction.async {
    statsOrchestrator.getReportEngagementReview(companyId).map(x => Ok(Json.toJson(x)))
  }

  def getReportsTagsDistribution(companyId: Option[UUID]) = SecuredAction.async { request =>
    statsOrchestrator.getReportsTagsDistribution(companyId, request.identity).map(x => Ok(Json.toJson(x)))
  }

  def getReportsStatusDistribution(companyId: Option[UUID]) = SecuredAction.async { request =>
    statsOrchestrator.getReportsStatusDistribution(companyId, request.identity).map(x => Ok(Json.toJson(x)))
  }

  def getAcceptedResponsesDistribution(companyId: UUID) =
    SecuredAction.andThen(WithRole(UserRole.AdminsAndReadOnlyAndAgents)).async { request =>
      statsOrchestrator
        .getAcceptedResponsesDistribution(companyId, request.identity)
        .map(x => Ok(Json.toJson(x)))
    }

  def getProReportToTransmitStat() =
    SecuredAction.async { request =>
      // Includes the reports that we want to transmit to a pro
      // but we have not identified the company
      val filter = ReportFilter(
        visibleToPro = Some(true)
      )
      statsOrchestrator
        .getReportsCountCurve(Some(request.identity), filter)
        .map(curve => Ok(Json.toJson(curve)))
    }

  def getProReportTransmittedStat() = SecuredAction.async { request =>
    statsOrchestrator
      .getReportsCountCurve(Some(request.identity), transmittedReportsFilter)
      .map(curve => Ok(Json.toJson(curve)))
  }

  def getProReportResponseStat(responseTypeQuery: Option[List[ReportResponseType]]) =
    SecuredAction.async(parse.empty) { request =>
      val statusFilter = responseTypeQuery
        .filter(_.nonEmpty)
        .map(_.map(ReportStatus.fromResponseType))
        .getOrElse(statusWithProResponse)
      val filter = ReportFilter(status = statusFilter)
      statsOrchestrator
        .getReportsCountCurve(Some(request.identity), filter)
        .map(curve => Ok(Json.toJson(curve)))
    }

  def dgccrfAccountsCurve(ticks: Option[Int]) = SecuredAction.async(parse.empty) { _ =>
    statsOrchestrator.dgccrfAccountsCurve(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
  }

  def dgccrfActiveAccountsCurve(ticks: Option[Int]) = SecuredAction.async(parse.empty) { _ =>
    statsOrchestrator.dgccrfActiveAccountsCurve(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
  }

  def dgccrfSubscription(ticks: Option[Int]) = SecuredAction.async(parse.empty) { _ =>
    statsOrchestrator.dgccrfSubscription(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
  }

  def dgccrfControlsCurve(ticks: Option[Int]) = SecuredAction.async(parse.empty) { _ =>
    statsOrchestrator.dgccrfControlsCurve(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
  }

  def countByDepartments() =
    SecuredAction.andThen(WithRole(UserRole.AdminsAndReadOnlyAndCCRF)).async { implicit request =>
      val mapper = new QueryStringMapper(request.queryString)
      val start  = mapper.timeWithLocalDateRetrocompatStartOfDay("start")
      val end    = mapper.timeWithLocalDateRetrocompatEndOfDay("end")
      statsOrchestrator.countByDepartments(start, end).map(res => Ok(Json.toJson(res)))
    }

  def reportsCountBySubcategories() =
    SecuredAction.andThen(WithRole(UserRole.AdminsAndReadOnlyAndAgents)).async { implicit request =>
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
    SecuredAction.andThen(WithRole(UserRole.AdminsAndReadOnlyAndAgents)).async { implicit request =>
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
    SecuredAction.andThen(WithRole(UserRole.AdminsAndReadOnlyAndAgents)).async { _ =>
      statsOrchestrator
        .fetchAdminActionEvents(companyId, reportAdminActionType)
        .map(count => Ok(Json.obj("value" -> count)))
    }

}
