package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models._
import orchestrators.StatsOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import utils.QueryStringMapper
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class StatisticController @Inject() (
    _stats: StatsOrchestrator,
    val silhouette: Silhouette[AuthEnv]
)(implicit val executionContext: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def getReportsCount() = UserAwareAction.async { request =>
    ReportFilter
      .fromQueryString(request.queryString, Some(UserRoles.Admin))
      .fold(
        error => {
          logger.error("Cannot parse querystring", error)
          Future.successful(BadRequest)
        },
        filters =>
          _stats
            .getReportCount(filters)
            .map(count => Ok(Json.obj("value" -> count)))
      )
  }

  /** Nom de fonction adoubé par Saïd. En cas d'incompréhension, merci de le contacter directement
    */
  def getReportsCountCurve() = UserAwareAction.async { request =>
    ReportFilter
      .fromQueryString(request.queryString, Some(UserRoles.Admin))
      .fold(
        error => {
          logger.error("Cannot parse querystring", error)
          Future.successful(BadRequest)
        },
        filters => {
          val mapper = new QueryStringMapper(request.queryString)
          val ticks = mapper.int("ticks").getOrElse(12)
          val tickDuration = mapper
            .string("tickDuration")
            .flatMap(CurveTickDuration.namesToValuesMap.get)
            .getOrElse(CurveTickDuration.Month)
          _stats.getReportsCountCurve(filters, ticks, tickDuration).map(curve => Ok(Json.toJson(curve)))
        }
      )
  }

  def getDelayReportReadInHours(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _stats
      .getReadAvgDelay(companyId)
      .map(count => Ok(Json.toJson(StatsValue(count.map(_.toHours.toInt)))))
  }

  def getDelayReportResponseInHours(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _stats
      .getResponseAvgDelay(companyId: Option[UUID])
      .map(count => Ok(Json.toJson(StatsValue(count.map(_.toHours.toInt)))))
  }

  def getReportResponseReviews(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _stats.getReportResponseReview(companyId).map(x => Ok(Json.toJson(x)))
  }

  def getReportsTagsDistribution(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _stats.getReportsTagsDistribution(companyId).map(x => Ok(Json.toJson(x)))
  }

  def getReportsStatusDistribution(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _stats.getReportsStatusDistribution(companyId).map(x => Ok(Json.toJson(x)))
  }
}
