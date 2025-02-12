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

  def getReportsCount() = Act.secured.all.allowImpersonation.async { request =>
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
  def getReportsCountCurve() = Act.secured.all.allowImpersonation.async { request =>
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

  def getDelayReportResponseInHours(companyId: Option[UUID]) = Act.secured.all.allowImpersonation.async { request =>
    statsOrchestrator
      .getResponseAvgDelay(companyId: Option[UUID], request.identity.userRole)
      .map(count => Ok(Json.toJson(StatsValue(count.map(_.toHours.toInt)))))
  }

  def getReportResponseReviews(companyId: Option[UUID]) = Act.secured.all.allowImpersonation.async {
    statsOrchestrator.getReportResponseReview(companyId).map(x => Ok(Json.toJson(x)))
  }

  def getReportEngagementReviews(companyId: Option[UUID]) = Act.secured.all.allowImpersonation.async {
    statsOrchestrator.getReportEngagementReview(companyId).map(x => Ok(Json.toJson(x)))
  }

  def getReportsTagsDistribution(companyId: Option[UUID]) = Act.secured.all.allowImpersonation.async { request =>
    statsOrchestrator.getReportsTagsDistribution(companyId, request.identity).map(x => Ok(Json.toJson(x)))
  }

  def getReportsStatusDistribution(companyId: Option[UUID]) = Act.secured.all.allowImpersonation.async { request =>
    statsOrchestrator.getReportsStatusDistribution(companyId, request.identity).map(x => Ok(Json.toJson(x)))
  }

  def getAcceptedResponsesDistribution(companyId: UUID) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async { request =>
      statsOrchestrator
        .getAcceptedResponsesDistribution(companyId, request.identity)
        .map(x => Ok(Json.toJson(x)))
    }

  def getProReportToTransmitStat() =
    Act.secured.all.allowImpersonation.async { request =>
      // Includes the reports that we want to transmit to a pro
      // but we have not identified the company
      val filter = ReportFilter(
        visibleToPro = Some(true)
      )
      statsOrchestrator
        .getReportsCountCurve(Some(request.identity), filter)
        .map(curve => Ok(Json.toJson(curve)))
    }

  def getProReportTransmittedStat() = Act.secured.all.allowImpersonation.async { request =>
    statsOrchestrator
      .getReportsCountCurve(Some(request.identity), transmittedReportsFilter)
      .map(curve => Ok(Json.toJson(curve)))
  }

  def getProReportResponseStat(responseTypeQuery: Option[List[ReportResponseType]]) =
    Act.secured.all.allowImpersonation.async(parse.empty) { request =>
      val statusFilter = responseTypeQuery
        .filter(_.nonEmpty)
        .map(_.map(ReportStatus.fromResponseType))
        .getOrElse(statusWithProResponse)
      val filter = ReportFilter(status = statusFilter)
      statsOrchestrator
        .getReportsCountCurve(Some(request.identity), filter)
        .map(curve => Ok(Json.toJson(curve)))
    }

  def dgccrfAccountsCurve(ticks: Option[Int]) = Act.secured.all.allowImpersonation.async(parse.empty) { _ =>
    statsOrchestrator.dgccrfAccountsCurve(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
  }

  def dgccrfActiveAccountsCurve(ticks: Option[Int]) = Act.secured.all.allowImpersonation.async(parse.empty) { _ =>
    statsOrchestrator.dgccrfActiveAccountsCurve(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
  }

  def dgccrfSubscription(ticks: Option[Int]) = Act.secured.all.allowImpersonation.async(parse.empty) { _ =>
    statsOrchestrator.dgccrfSubscription(ticks.getOrElse(12)).map(x => Ok(Json.toJson(x)))
  }

  def dgccrfControlsCurve(ticks: Option[Int]) = Act.secured.all.allowImpersonation.async(parse.empty) { _ =>
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
