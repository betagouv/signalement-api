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
      reportsCountSendedToPro <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.AUTHORIZED), event = Some(ENVOI_SIGNALEMENT))
      reportsCountPromise <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.AUTHORIZED), event = Some(REPONSE_PRO_SIGNALEMENT))
      reportsCountWithoutSiret <- reportRepository.nbSignalementsBetweenDates(withoutSiret = true)
      reportsCountByCategory <- reportRepository.nbSignalementsByCategory()
      reportsCountAura <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.AURA))
      reportsCountCdvl <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.CDVL))
      reportsCountOcc <- reportRepository.nbSignalementsBetweenDates(departments = Some(Departments.OCC))
      reportsDurationsForEnvoiSignalement <- reportRepository.avgDurationsForEvent(ENVOI_SIGNALEMENT)

    } yield {

      val reportsCountByRegionList = Seq(
        ReportsByRegion("AURA", reportsCountAura.getOrElse(0)),
        ReportsByRegion("CDVL", reportsCountCdvl.getOrElse(0)),
        ReportsByRegion("OCC", reportsCountOcc.getOrElse(0)))

      Ok(Json.toJson(
        Statistics(
          reportsCount,
          reportsPerMonth.filter(stat => stat.yearMonth.isAfter(YearMonth.now().minusYears(1))),
          reportsCount7Days.getOrElse(0),
          reportsCount30Days.getOrElse(0),
          reportsCountInRegion.getOrElse(0),
          reportsCount7DaysInRegion.getOrElse(0),
          reportsCount30DaysInRegion.getOrElse(0),
          reportsCountSendedToPro.getOrElse(0),
          reportsCountPromise.getOrElse(0),
          reportsCountWithoutSiret.getOrElse(0),
          reportsCountByCategory.toList,
          reportsCountByRegionList,
          reportsDurationsForEnvoiSignalement.getOrElse(0)
        )
      ))
    }
  }

}
