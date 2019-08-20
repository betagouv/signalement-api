package controllers

import java.time.{LocalDateTime, YearMonth}

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models._
import play.api.libs.json.Json
import play.api.{Configuration, Environment, Logger}
import repositories._
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.Departments
import utils.Constants.StatusPro.{A_TRAITER, PROMESSE_ACTION, SIGNALEMENT_CONSULTE_IGNORE, SIGNALEMENT_INFONDE, SIGNALEMENT_NON_CONSULTE, SIGNALEMENT_TRANSMIS, TRAITEMENT_EN_COURS}
import utils.DateUtils
import utils.silhouette.AuthEnv

import scala.concurrent.ExecutionContext

class StatisticController @Inject()(reportRepository: ReportRepository,
                                    eventRepository: EventRepository,
                                    userRepository: UserRepository,
                                    mailerService: MailerService,
                                    s3Service: S3Service,
                                    val silhouette: Silhouette[AuthEnv],
                                    configuration: Configuration,
                                    environment: Environment)
                                   (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def getStatistics = UserAwareAction.async { implicit request =>

    for {
      reportsCount <- reportRepository.count
      reportsPerMonth <- reportRepository.countPerMonth
      reportsCount7Days <- reportRepository.nbSignalementsBetweenDates(DateUtils.formatTime(LocalDateTime.now().minusDays(7)))
      reportsCount30Days <- reportRepository.nbSignalementsBetweenDates(DateUtils.formatTime(LocalDateTime.now().minusDays(30)))
      reportsCountInRegion <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.AUTHORIZED))
      reportsCount7DaysInRegion <- reportRepository.nbSignalementsBetweenDates(start = DateUtils.formatTime(LocalDateTime.now().minusDays(7)), departments = Some(Departments.AUTHORIZED))
      reportsCount30DaysInRegion <- reportRepository.nbSignalementsBetweenDates(start = DateUtils.formatTime(LocalDateTime.now().minusDays(30)), departments = Some(Departments.AUTHORIZED))
      reportsCountSendedToPro <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.AUTHORIZED), statusList = Some(List(SIGNALEMENT_TRANSMIS, PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_CONSULTE_IGNORE)))
      reportsCountSendedToProBase <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.AUTHORIZED), statusList = Some(List(A_TRAITER, TRAITEMENT_EN_COURS, SIGNALEMENT_TRANSMIS, PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_NON_CONSULTE, SIGNALEMENT_CONSULTE_IGNORE)))
      reportsCountPromise <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.AUTHORIZED), statusList = Some(List(PROMESSE_ACTION)))
      reportsCountPromiseBase <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.AUTHORIZED), statusList = Some(List(SIGNALEMENT_TRANSMIS, PROMESSE_ACTION, SIGNALEMENT_INFONDE, SIGNALEMENT_NON_CONSULTE, SIGNALEMENT_CONSULTE_IGNORE)))
      reportsCountWithoutSiret <- reportRepository.nbSignalementsBetweenDates(withoutSiret = true)
      reportsCountByCategory <- reportRepository.nbSignalementsByCategory()
      reportsCountAura <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.AURA))
      reportsCountCdvl <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.CDVL))
      reportsCountOcc <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.OCC))
      reportsDurationsForEnvoiSignalement <- reportRepository.avgDurationsForSendingReport()

    } yield {

      val reportsCountByRegionList = Seq(
        ReportsByRegion("AURA", reportsCountAura.getOrElse(0)),
        ReportsByRegion("CDVL", reportsCountCdvl.getOrElse(0)),
        ReportsByRegion("OCC", reportsCountOcc.getOrElse(0)))

      val sendedToProBase = reportsCountSendedToProBase match {
        case None => 1
        case Some(0) => 1
        case Some(other) => other
      }

      val promiseBase: Int = reportsCountPromiseBase match {
        case None => 1
        case Some(0) => 1
        case Some(other) => other
      }

      val reportsCountBase = reportsCount match {
        case 0 => 1
        case other => other
      }

      Ok(Json.toJson(
        Statistics(
          reportsCount,
          reportsPerMonth.filter(stat => stat.yearMonth.isAfter(YearMonth.now().minusYears(1))),
          reportsCount7Days.getOrElse(0),
          reportsCount30Days.getOrElse(0),
          reportsCountInRegion.getOrElse(0),
          reportsCount7DaysInRegion.getOrElse(0),
          reportsCount30DaysInRegion.getOrElse(0),
          BigDecimal(reportsCountSendedToPro.getOrElse(0).asInstanceOf[Double] / sendedToProBase * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
          BigDecimal(reportsCountPromise.getOrElse(0).asInstanceOf[Double] / promiseBase * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
          BigDecimal(reportsCountWithoutSiret.getOrElse(0).asInstanceOf[Double] / reportsCountBase * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
          reportsCountByCategory.toList,
          reportsCountByRegionList,
          reportsDurationsForEnvoiSignalement.getOrElse(0)
        )
      ))
    }
  }

}
