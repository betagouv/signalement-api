package controllers

import com.mohiva.play.silhouette.api.Silhouette
import models._
import play.api.Configuration
import play.api.Logger
import play.api.libs.json.Json
import repositories._
import utils.Constants.ReportStatus
import utils.Constants.ReportStatus._
import utils.silhouette.auth.AuthEnv

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class StatisticController @Inject() (
    reportRepository: ReportRepository,
    val silhouette: Silhouette[AuthEnv],
    configuration: Configuration
)(implicit val executionContext: ExecutionContext)
    extends BaseController {

  val logger: Logger = Logger(this.getClass)
  val cutoff = configuration.getOptional[String]("play.stats.globalStatsCutoff").map(java.time.Duration.parse(_))

  def getReportCount = UserAwareAction.async { implicit request =>
    reportRepository.count().map(count => Ok(Json.obj("value" -> count)))
  }

  def getMonthlyReportCount = UserAwareAction.async { implicit request =>
    reportRepository.monthlyCount.map(monthlyStats => Ok(Json.toJson(monthlyStats)))
  }

  def getReportForwardedToProPercentage = UserAwareAction.async { implicit request =>
    for {
      count <- reportRepository.countWithStatus(
        ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList,
        cutoff
      )
      baseCount <- reportRepository.countWithStatus(
        ReportStatus.reportStatusList.toList,
        cutoff
      )
    } yield Ok(
      Json.obj(
        "value" -> count * 100 / baseCount
      )
    )
  }

  def getReportReadByProPercentage = UserAwareAction.async { implicit request =>
    for {
      count <- reportRepository.countWithStatus(
        List(
          SIGNALEMENT_TRANSMIS,
          PROMESSE_ACTION,
          SIGNALEMENT_INFONDE,
          SIGNALEMENT_MAL_ATTRIBUE,
          SIGNALEMENT_CONSULTE_IGNORE
        ),
        cutoff
      )
      baseCount <- reportRepository.countWithStatus(
        ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList,
        cutoff
      )
    } yield Ok(
      Json.obj(
        "value" -> count * 100 / baseCount
      )
    )
  }

  def getMonthlyReportForwardedToProPercentage = UserAwareAction.async { implicit request =>
    for {
      monthlyCounts <- reportRepository.countMonthlyWithStatus(
        ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList
      )
      monthlyBaseCounts <- reportRepository.countMonthlyWithStatus(ReportStatus.reportStatusList.toList)
    } yield Ok(
      Json.toJson(
        monthlyBaseCounts.map(monthlyBaseCount =>
          MonthlyStat(
            monthlyCounts
              .find(_.yearMonth == monthlyBaseCount.yearMonth)
              .map(_.value)
              .getOrElse(0) * 100 / monthlyBaseCount.value,
            monthlyBaseCount.yearMonth
          )
        )
      )
    )
  }

  def getMonthlyReportReadByProPercentage = UserAwareAction.async { implicit request =>
    for {
      monthlyCounts <- reportRepository.countMonthlyWithStatus(
        List(
          SIGNALEMENT_TRANSMIS,
          PROMESSE_ACTION,
          SIGNALEMENT_INFONDE,
          SIGNALEMENT_MAL_ATTRIBUE,
          SIGNALEMENT_CONSULTE_IGNORE
        )
      )
      monthlyBaseCounts <- reportRepository.countMonthlyWithStatus(
        ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList
      )
    } yield Ok(
      Json.toJson(
        monthlyBaseCounts.map(monthlyBaseCount =>
          MonthlyStat(
            monthlyCounts
              .find(_.yearMonth == monthlyBaseCount.yearMonth)
              .map(_.value)
              .getOrElse(0) * 100 / monthlyBaseCount.value,
            monthlyBaseCount.yearMonth
          )
        )
      )
    )
  }

  def getReportWithResponsePercentage = UserAwareAction.async { implicit request =>
    for {
      count <- reportRepository.countWithStatus(
        List(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE),
        cutoff
      )
      baseCount <- reportRepository.countWithStatus(
        List(
          SIGNALEMENT_TRANSMIS,
          PROMESSE_ACTION,
          SIGNALEMENT_INFONDE,
          SIGNALEMENT_MAL_ATTRIBUE,
          SIGNALEMENT_CONSULTE_IGNORE
        ),
        cutoff
      )
    } yield Ok(
      Json.obj(
        "value" -> count * 100 / baseCount
      )
    )
  }

  def getMonthlyReportWithResponsePercentage = UserAwareAction.async { implicit request =>
    for {
      monthlyCounts <-
        reportRepository.countMonthlyWithStatus(List(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE))
      monthlyBaseCounts <- reportRepository.countMonthlyWithStatus(
        List(
          SIGNALEMENT_TRANSMIS,
          PROMESSE_ACTION,
          SIGNALEMENT_INFONDE,
          SIGNALEMENT_MAL_ATTRIBUE,
          SIGNALEMENT_CONSULTE_IGNORE
        )
      )
    } yield Ok(
      Json.toJson(
        monthlyBaseCounts.map(monthlyBaseCount =>
          MonthlyStat(
            monthlyCounts
              .find(_.yearMonth == monthlyBaseCount.yearMonth)
              .map(_.value)
              .getOrElse(0) * 100 / monthlyBaseCount.value,
            monthlyBaseCount.yearMonth
          )
        )
      )
    )
  }

  def getReportWithWebsitePercentage = UserAwareAction.async { implicit request =>
    for {
      count <- reportRepository.countWithStatus(
        ReportStatus.reportStatusList.toList,
        cutoff,
        Some(true)
      )
      baseCount <- reportRepository.countWithStatus(
        ReportStatus.reportStatusList.toList,
        cutoff
      )
    } yield Ok(
      Json.obj(
        "value" -> count * 100 / baseCount
      )
    )
  }

  def getReadAvgTimeInMinutes(companyId: Option[UUID] = None) = UnsecuredAction.async { implicit request =>
    reportRepository
      .getReadAvgTime(companyId)
      .map(count => Ok(Json.obj("value" -> count.map(_.toMinutes))))
  }

  def getResponseAvgTimeInMinutes(companyId: Option[UUID] = None) = UnsecuredAction.async { implicit request =>
    reportRepository
      .getResponseAvgTime(companyId)
      .map(count => Ok(Json.obj("value" -> count.map(_.toMinutes))))
  }

  def getReadAvgTimeInMinutesByCompany(companyId: UUID) = getReadAvgTimeInMinutes(Some(companyId))

  def getResponseAvgTimeInMinutesByCompany(companyId: UUID) = getResponseAvgTimeInMinutes(Some(companyId))
}
