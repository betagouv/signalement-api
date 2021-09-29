package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models._
import orchestrators.StatsOrchestrator
import play.api.Logger
import play.api.libs.json.Json
import utils.Constants.ReportStatus
import utils.Constants.ReportStatus._
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class StatisticController @Inject() (
    _companyStats: StatsOrchestrator,
    val silhouette: Silhouette[AuthEnv]
)(implicit val executionContext: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def getReportCount(companyId: Option[UUID]) = UserAwareAction.async { implicit request =>
    _companyStats.getReportCount(companyId).map(count => Ok(Json.obj("value" -> count)))
  }

  def getReportForwardedToProPercentage(companyId: Option[UUID]) = UserAwareAction.async { implicit request =>
    _companyStats
      .getReportWithStatusPercent(
        status = ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList,
        companyId = companyId
      )
      .map(percent => Ok(Json.toJson(StatsValue(Some(percent)))))
  }

  def getReportReadByProPercentage(companyId: Option[UUID]) = UserAwareAction.async { implicit request =>
    _companyStats
      .getReportWithStatusPercent(
        status = List(
          SIGNALEMENT_TRANSMIS,
          PROMESSE_ACTION,
          SIGNALEMENT_INFONDE,
          SIGNALEMENT_MAL_ATTRIBUE,
          SIGNALEMENT_CONSULTE_IGNORE
        ),
        baseStatus = ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList,
        companyId = companyId
      )
      .map(percent => Ok(Json.toJson(StatsValue(Some(percent)))))
  }

  def getReportWithResponsePercentage(companyId: Option[UUID]) = UserAwareAction.async { implicit request =>
    _companyStats
      .getReportWithStatusPercent(
        status = List(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE),
        baseStatus = List(
          SIGNALEMENT_TRANSMIS,
          PROMESSE_ACTION,
          SIGNALEMENT_INFONDE,
          SIGNALEMENT_MAL_ATTRIBUE,
          SIGNALEMENT_CONSULTE_IGNORE
        ),
        companyId = companyId
      )
      .map(percent => Ok(Json.toJson(StatsValue(Some(percent)))))
  }

  def getDailyReportCount(companyId: Option[UUID], ticks: Option[Int]) = UserAwareAction.async { implicit request =>
    _companyStats
      .getReportsCountDaily(List(), companyId, ticks.getOrElse(11))
      .map(monthlyStats => Ok(Json.toJson(monthlyStats)))
  }

  def getDailyReportsWithResponseCount(companyId: Option[UUID], ticks: Option[Int]) = UserAwareAction.async {
    implicit request =>
      _companyStats
        .getReportsCountDaily(
          status = List(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE),
          companyId = companyId,
          ticks = ticks.getOrElse(11)
        )
        .map(monthlyStats => Ok(Json.toJson(monthlyStats)))
  }
  def getMonthlyReportCount(companyId: Option[UUID], ticks: Option[Int]) = UserAwareAction.async { implicit request =>
    _companyStats
      .getReportsCountMonthly(
        status = List(),
        companyId = companyId,
        ticks = ticks.getOrElse(11)
      )
      .map(monthlyStats => Ok(Json.toJson(monthlyStats)))
  }

  def getMonthlyReportsWithResponseCount(companyId: Option[UUID], ticks: Option[Int]) = UserAwareAction.async {
    implicit request =>
      _companyStats
        .getReportsCountMonthly(
          status = List(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE),
          companyId = companyId,
          ticks = ticks.getOrElse(11)
        )
        .map(monthlyStats => Ok(Json.toJson(monthlyStats)))
  }

  def getMonthlyReportForwardedToProPercentage(companyId: Option[UUID], ticks: Option[Int]) = UserAwareAction.async {
    implicit request =>
      _companyStats
        .getMonthlyReportWithStatusPercentage(
          status = ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList,
          companyId = companyId,
          ticks = ticks.getOrElse(11)
        )
        .map(value => Ok(Json.toJson(value)))
  }

  def getMonthlyReportReadByProPercentage(companyId: Option[UUID], ticks: Option[Int]) = UserAwareAction.async {
    implicit request =>
      _companyStats
        .getMonthlyReportWithStatusPercentage(
          status = List(
            SIGNALEMENT_TRANSMIS,
            PROMESSE_ACTION,
            SIGNALEMENT_INFONDE,
            SIGNALEMENT_MAL_ATTRIBUE,
            SIGNALEMENT_CONSULTE_IGNORE
          ),
          baseStatus = ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList,
          companyId = companyId,
          ticks = ticks.getOrElse(11)
        )
        .map(value => Ok(Json.toJson(value)))
  }

  def getMonthlyReportWithResponsePercentage(companyId: Option[UUID], ticks: Option[Int]) = UserAwareAction.async {
    implicit request =>
      _companyStats
        .getMonthlyReportWithStatusPercentage(
          status = List(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE),
          baseStatus = List(
            SIGNALEMENT_TRANSMIS,
            PROMESSE_ACTION,
            SIGNALEMENT_INFONDE,
            SIGNALEMENT_MAL_ATTRIBUE,
            SIGNALEMENT_CONSULTE_IGNORE
          ),
          companyId = companyId,
          ticks = ticks.getOrElse(11)
        )
        .map(value => Ok(Json.toJson(value)))
  }

  def getReportWithWebsitePercentage(companyId: Option[UUID]) = UserAwareAction.async { implicit request =>
    _companyStats.getReportHavingWebsitePercentage(companyId).map(percent => Ok(Json.toJson(StatsValue(Some(percent)))))
  }

  def getReportReadAvgDelayInHours(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async { implicit request =>
    _companyStats
      .getReadAvgDelay(companyId)
      .map(count => Ok(Json.toJson(StatsValue(count.map(_.toHours.toInt)))))
  }

  def getReportResponseAvgDelayInHours(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async { implicit request =>
    _companyStats
      .getResponseAvgDelay(companyId: Option[UUID])
      .map(count => Ok(Json.toJson(StatsValue(count.map(_.toHours.toInt)))))
  }

  def getReportResponseReviews(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _companyStats.getReportResponseReview(companyId).map(x => Ok(Json.toJson(x)))
  }

  def getReportsTagsDistribution(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _companyStats.getReportsTagsDistribution(companyId).map(x => Ok(Json.toJson(x)))
  }

  def getReportsStatusDistribution(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _companyStats.getReportsStatusDistribution(companyId).map(x => Ok(Json.toJson(x)))
  }
}
