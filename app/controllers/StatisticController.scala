package controllers

import java.time.{Duration, LocalDateTime, YearMonth}

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models._
import play.api.libs.json.Json
import play.api.{Configuration, Logger}
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
                                    configuration: Configuration)
                                   (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)
  val cutoff = configuration.getOptional[String]("play.stats.globalStatsCutoff").map(java.time.Duration.parse(_))

  def getReportCount = UserAwareAction.async { implicit request =>
    reportRepository.count().map(count => Ok(Json.obj("value" -> count)))
  }

  def getMonthlyReportCount = UserAwareAction.async { implicit request =>
    reportRepository.monthlyCount.map(monthlyStats => Ok(Json.toJson(monthlyStats)))
  }

  def getReportReadByProPercentage = UserAwareAction.async { implicit request =>
    for {
      count <- reportRepository.countWithStatus(
        List(SIGNALEMENT_TRANSMIS, PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE, SIGNALEMENT_CONSULTE_IGNORE),
        cutoff
      )
      baseCount <- reportRepository.countWithStatus(
        ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList,
        cutoff
      )
    } yield
      Ok(Json.obj(
        "value" -> count * 100 / baseCount
      ))
  }

  def getMonthlyReportReadByProPercentage = UserAwareAction.async { implicit request =>
    for {
      monthlyCounts <- reportRepository.countMonthlyWithStatus(List(SIGNALEMENT_TRANSMIS, PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE, SIGNALEMENT_CONSULTE_IGNORE))
      monthlyBaseCounts <- reportRepository.countMonthlyWithStatus(ReportStatus.reportStatusList.filterNot(Set(NA, EMPLOYEE_REPORT)).toList)
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
      count <- reportRepository.countWithStatus(
        List(PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE),
        cutoff
      )
      baseCount <- reportRepository.countWithStatus(
        List(SIGNALEMENT_TRANSMIS, PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_MAL_ATTRIBUE, SIGNALEMENT_CONSULTE_IGNORE),
        cutoff
      )
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
    reportDataRepository.getReportReadMedianDelay.map(count => Ok(Json.obj("value" -> Duration.ofMinutes(count.toLong))))
  }

  def getReportWithResponseMedianDelay = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    reportDataRepository.getReportResponseMedianDelay.map(count => Ok(Json.obj("value" -> Duration.ofMinutes(count.toLong))))
  }

  def updateReportData() = SecuredAction(WithRole(UserRoles.Admin)).async { implicit request =>
    for {
      _ <- reportDataRepository.updateReportReadDelay
      _ <- reportDataRepository.updateReportResponseDelay
    } yield Ok("ReportData updated")
  }
}
