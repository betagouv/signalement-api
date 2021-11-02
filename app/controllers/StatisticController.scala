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

  def getReportCount(companyId: Option[UUID]) = UserAwareAction.async { _ =>
    _companyStats.getReportCount(companyId).map(count => Ok(Json.obj("value" -> count)))
  }

  def getPercentageReportForwarded(companyId: Option[UUID]) = UserAwareAction.async { _ =>
    _companyStats
      .getReportWithStatusPercent(
        status = ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList,
        companyId = companyId
      )
      .map(percent => Ok(Json.toJson(StatsValue(Some(percent)))))
  }

  def getPercentageReportRead(companyId: Option[UUID]) = UserAwareAction.async { _ =>
    _companyStats
      .getReportWithStatusPercent(
        status = Seq(
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

  def getPercentageReportResponded(companyId: Option[UUID]) = UserAwareAction.async { _ =>
    _companyStats
      .getReportWithStatusPercent(
        status = Seq(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE),
        baseStatus = Seq(
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

  def getPercentageReportWithWebsite(companyId: Option[UUID]) = UserAwareAction.async { _ =>
    _companyStats.getReportHavingWebsitePercentage(companyId).map(percent => Ok(Json.toJson(StatsValue(Some(percent)))))
  }

  private[this] def getTickDuration(tickDuration: Option[String]): CurveTickDuration =
    tickDuration.flatMap(CurveTickDuration.namesToValuesMap.get).getOrElse(CurveTickDuration.Month)

  private[this] def getTicks(ticks: Option[Int]): Int = ticks.getOrElse(12)

  def getCurveReportCount(
      companyId: Option[UUID],
      ticks: Option[Int],
      tickDuration: Option[String],
      status: Seq[String]
  ) =
    UserAwareAction.async {
      _companyStats
        .getReportsCountCurve(
          companyId = companyId,
          status = status.map(ReportStatus.fromDefaultValue),
          ticks = getTicks(ticks),
          tickDuration = getTickDuration(tickDuration)
        )
        .map(curve => Ok(Json.toJson(curve)))
    }

//  def getCurveReportsRespondedCount(companyId: Option[UUID], ticks: Option[Int], tickDuration: Option[String]) =
//    UserAwareAction.async {
//      _companyStats
//        .getReportsCountCurve(
//          companyId = companyId,
//          status = Seq(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE),
//          ticks = getTicks(ticks),
//          tickDuration = getTickDuration(tickDuration)
//        )
//        .map(stats => Ok(Json.toJson(stats)))
//    }

  def getCurveReportsRespondedCount(companyId: Option[UUID], ticks: Option[Int], tickDuration: Option[String]) =
    UserAwareAction.async {
      _companyStats
        .getReportsCountCurve(
          companyId = companyId,
          status = Seq(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE),
          ticks = getTicks(ticks),
          tickDuration = getTickDuration(tickDuration)
        )
        .map(stats => Ok(Json.toJson(stats)))
    }

  def getCurveReportForwardedPercentage(
      companyId: Option[UUID],
      ticks: Option[Int],
      tickDuration: Option[String]
  ) =
    UserAwareAction.async {
      _companyStats
        .getReportWithStatusPercentageCurve(
          companyId = companyId,
          status = ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList,
          ticks = getTicks(ticks),
          tickDuration = getTickDuration(tickDuration)
        )
        .map(value => Ok(Json.toJson(value)))
    }

  def getCurveReportReadPercentage(companyId: Option[UUID], ticks: Option[Int], tickDuration: Option[String]) =
    UserAwareAction.async {
      _companyStats
        .getReportWithStatusPercentageCurve(
          companyId = companyId,
          status = Seq(
            SIGNALEMENT_TRANSMIS,
            PROMESSE_ACTION,
            SIGNALEMENT_INFONDE,
            SIGNALEMENT_MAL_ATTRIBUE,
            SIGNALEMENT_CONSULTE_IGNORE
          ),
          baseStatus = ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList,
          ticks = getTicks(ticks),
          tickDuration = getTickDuration(tickDuration)
        )
        .map(value => Ok(Json.toJson(value)))
    }

  def getCurveReportRespondedPercentage(companyId: Option[UUID], ticks: Option[Int], tickDuration: Option[String]) =
    UserAwareAction.async {
      _companyStats
        .getReportWithStatusPercentageCurve(
          companyId = companyId,
          status = Seq(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE),
          baseStatus = Seq(
            SIGNALEMENT_TRANSMIS,
            PROMESSE_ACTION,
            SIGNALEMENT_INFONDE,
            SIGNALEMENT_MAL_ATTRIBUE,
            SIGNALEMENT_CONSULTE_IGNORE
          ),
          ticks = getTicks(ticks),
          tickDuration = getTickDuration(tickDuration)
        )
        .map(value => Ok(Json.toJson(value)))
    }

  def getDelayReportReadInHours(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
    _companyStats
      .getReadAvgDelay(companyId)
      .map(count => Ok(Json.toJson(StatsValue(count.map(_.toHours.toInt)))))
  }

  def getDelayReportResponseInHours(companyId: Option[UUID]) = SecuredAction(
    WithRole(UserRoles.Admin, UserRoles.DGCCRF)
  ).async {
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
