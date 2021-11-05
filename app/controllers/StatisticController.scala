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

  def getReportCount(companyId: Option[UUID], status: Seq[String]) = UserAwareAction.async { _ =>
    _companyStats
      .getReportCount(companyId, status.map(ReportStatus2.withName))
      .map(count => Ok(Json.obj("value" -> count)))
  }

  def getPercentageReportForwarded(companyId: Option[UUID]) = UserAwareAction.async { _ =>
    _companyStats
      .getReportWithStatusPercent(
        status = ReportStatus2.values.filterNot(Set(ReportStatus2.Na, ReportStatus2.EmployeeReport)).toList,
        companyId = companyId
      )
      .map(percent => Ok(Json.toJson(StatsValue(Some(percent)))))
  }

  def getPercentageReportRead(companyId: Option[UUID]) = UserAwareAction.async { _ =>
    _companyStats
      .getReportWithStatusPercent(
        status = Seq(
          ReportStatus2.SignalementTransmis,
          ReportStatus2.PromesseAction,
          ReportStatus2.SignalementInfonde,
          ReportStatus2.SignalementMalAttribue,
          ReportStatus2.SignalementConsulteIgnore
        ),
        baseStatus = ReportStatus2.values.filterNot(Set(ReportStatus2.Na, ReportStatus2.EmployeeReport)).toList,
        companyId = companyId
      )
      .map(percent => Ok(Json.toJson(StatsValue(Some(percent)))))
  }

  def getPercentageReportResponded(companyId: Option[UUID]) = UserAwareAction.async { _ =>
    _companyStats
      .getReportWithStatusPercent(
        status = Seq(
          ReportStatus2.PromesseAction,
          ReportStatus2.SignalementInfonde,
          ReportStatus2.SignalementMalAttribue
        ),
        baseStatus = Seq(
          ReportStatus2.SignalementTransmis,
          ReportStatus2.PromesseAction,
          ReportStatus2.SignalementInfonde,
          ReportStatus2.SignalementMalAttribue,
          ReportStatus2.SignalementConsulteIgnore
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
          status = status.map(ReportStatus2.withName),
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
//          status = Seq(ReportStatus2.PromesseAction, ReportStatus2.SignalementInfonde, ReportStatus2.SignalementMalAttribue),
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
          status = Seq(
            ReportStatus2.PromesseAction,
            ReportStatus2.SignalementInfonde,
            ReportStatus2.SignalementMalAttribue
          ),
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
          status = ReportStatus.reportStatusList.filterNot(Set(ReportStatus2.Na, ReportStatus2.EmployeeReport)).toList,
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
            ReportStatus2.SignalementTransmis,
            ReportStatus2.PromesseAction,
            ReportStatus2.SignalementInfonde,
            ReportStatus2.SignalementMalAttribue,
            ReportStatus2.SignalementConsulteIgnore
          ),
          baseStatus = ReportStatus.reportStatusList.filterNot(Set(ReportStatus2.Na, ReportStatus2.EmployeeReport)).toList,
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
          status = Seq(ReportStatus2.PromesseAction, ReportStatus2.SignalementInfonde, ReportStatus2.SignalementMalAttribue),
          baseStatus = Seq(
            ReportStatus2.SignalementTransmis,
            ReportStatus2.PromesseAction,
            ReportStatus2.SignalementInfonde,
            ReportStatus2.SignalementMalAttribue,
            ReportStatus2.SignalementConsulteIgnore
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
