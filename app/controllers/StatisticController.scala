package controllers

import java.time.{Duration, LocalDateTime, YearMonth}

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models._
import play.api.libs.json.Json
import play.api.{Configuration, Environment, Logger}
import repositories._
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.{Departments, ReportStatus}
import utils.Constants.ReportStatus.{A_TRAITER, EMPLOYEE_REPORT, NA, PROMESSE_ACTION, SIGNALEMENT_CONSULTE_IGNORE, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE, SIGNALEMENT_NON_CONSULTE, SIGNALEMENT_TRANSMIS, TRAITEMENT_EN_COURS}
import utils.DateUtils
import utils.silhouette.auth.{AuthEnv, WithRole}

import scala.concurrent.ExecutionContext

class StatisticController @Inject()(reportRepository: ReportRepository,
                                    reportDataRepository: ReportDataRepository,
                                    userRepository: UserRepository,
                                    mailerService: MailerService,
                                    s3Service: S3Service,
                                    val silhouette: Silhouette[AuthEnv],
                                    configuration: Configuration,
                                    environment: Environment)
                                   (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def getReportCount = UserAwareAction.async { implicit request =>
    reportRepository.count().map(count => Ok(Json.obj("value" -> count)))
  }

  def getMonthlyReportCount = UserAwareAction.async { implicit request =>
    reportRepository.monthlyCount.map(monthlyStats => Ok(Json.toJson(monthlyStats)))
  }

  def getReportReadByProPercentage = UserAwareAction.async { implicit request =>
    for {
      count <- reportRepository.countWithStatus(List(SIGNALEMENT_TRANSMIS, PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE, SIGNALEMENT_CONSULTE_IGNORE))
      baseCount <- reportRepository.countWithStatus(ReportStatus.reportStatusList.filterNot(List(NA, EMPLOYEE_REPORT).contains(_)).toList)
    } yield
      Ok(Json.obj(
        "value" -> count * 100 / baseCount
      ))
  }

  def getMonthlyReportReadByProPercentage = UserAwareAction.async { implicit request =>
    for {
      monthlyCounts <- reportRepository.countMonthlyWithStatus(List(SIGNALEMENT_TRANSMIS, PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE, SIGNALEMENT_CONSULTE_IGNORE))
      monthlyBaseCounts <- reportRepository.countMonthlyWithStatus(ReportStatus.reportStatusList.filterNot(List(NA, EMPLOYEE_REPORT).contains(_)).toList)
    } yield
      Ok(Json.toJson(
        monthlyBaseCounts.map(
          monthlyBaseCount => MonthlyStat(
            monthlyCounts.find(_.yearMonth == monthlyBaseCount.yearMonth).map(_.value).getOrElse(0) * 100 / monthlyBaseCount.value,
            monthlyBaseCount.yearMonth
          )
        )
      ))
  }

  def getReportWithResponsePercentage = UserAwareAction.async { implicit request =>
    for {
      count <- reportRepository.countWithStatus(List(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE))
      baseCount <- reportRepository.countWithStatus(List(SIGNALEMENT_TRANSMIS, PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE, SIGNALEMENT_CONSULTE_IGNORE))
    } yield
      Ok(Json.obj(
        "value" -> count * 100 / baseCount
      ))
  }

  def getMonthlyReportWithResponsePercentage = UserAwareAction.async { implicit request =>
    for {
      monthlyCounts <- reportRepository.countMonthlyWithStatus(List(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE))
      monthlyBaseCounts <- reportRepository.countMonthlyWithStatus(List(SIGNALEMENT_TRANSMIS, PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE, SIGNALEMENT_CONSULTE_IGNORE))
    } yield
      Ok(Json.toJson(
        monthlyBaseCounts.map(
          monthlyBaseCount => MonthlyStat(
            monthlyCounts.find(_.yearMonth == monthlyBaseCount.yearMonth).map(_.value).getOrElse(0) * 100 / monthlyBaseCount.value,
            monthlyBaseCount.yearMonth
          )
        )
      ))
  }

  def getReportReadMedianDelay = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    reportDataRepository.getReportReadMedianDelay.map(count => Ok(Json.obj("value" -> Duration.ofMillis(count.toLong))))
  }

  def getReportWithResponseMedianDelay = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    reportDataRepository.getReportResponseMedianDelay.map(count => Ok(Json.obj("value" -> Duration.ofMillis(count.toLong))))
  }

  def updateReportData() = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>

    for {
      _ <- reportDataRepository.updateReportReadDelay
      _ <- reportDataRepository.updateReportResponseDelay
    } yield Ok("ReportData updated")

  }

}
