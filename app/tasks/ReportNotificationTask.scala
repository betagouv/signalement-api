package tasks

import java.net.URI
import java.time.temporal.ChronoUnit
import java.time.{DayOfWeek, LocalDate, LocalDateTime, LocalTime}

import akka.actor.ActorSystem
import javax.inject.Inject
import models.{Report, ReportCategory}
import play.api.{Configuration, Logger}
import repositories.{ReportFilter, ReportRepository, SubscriptionRepository}
import services.MailerService
import utils.Constants.Departments
import utils.EmailAddress

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ReportNotificationTask @Inject()(actorSystem: ActorSystem,
                                       reportRepository: ReportRepository,
                                       subscriptionRepository: SubscriptionRepository,
                                       mailerService: MailerService,
                                       configuration: Configuration)
                                      (implicit executionContext: ExecutionContext) {

  val logger: Logger = Logger(this.getClass())

  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val contactAddress = configuration.get[EmailAddress]("play.mail.contactAddress")

  val startTime = LocalTime.of(configuration.get[Int]("play.tasks.report.notification.start.hour"), configuration.get[Int]("play.tasks.report.notification.start.minute"), 0)

  val startDate = if (LocalTime.now.isAfter(startTime)) LocalDate.now.plusDays(1).atTime(startTime) else LocalDate.now.atTime(startTime)
  val initialDelay = (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds

  val departments = Departments.ALL

  actorSystem.scheduler.schedule(initialDelay = initialDelay, 1.days) {
    logger.debug(s"initialDelay - ${initialDelay}");

    if (LocalDate.now.getDayOfWeek == DayOfWeek.valueOf(configuration.get[String]("play.tasks.report.notification.weekly.dayOfWeek"))) {
      runWeeklyNotificationTask(LocalDate.now)
    }

    runDailyNotificationTask(LocalDate.now, Some(ReportCategory.COVID))
  }

  def runWeeklyNotificationTask(taskDate: LocalDate) = {

    logger.debug("Traitement de notification hebdomadaire des signalements")
    logger.debug(s"taskDate - ${taskDate}");

    reportRepository.getReports(
        0,
        10000,
        ReportFilter(start = Some(taskDate.minusDays(7)), end = Some(taskDate))
    ).map(reports =>{
      departments.foreach(department =>
        reports.entities.filter(report => report.companyPostalCode.map(_.startsWith(department)).getOrElse(false)) match {
          case departementReports if departementReports.nonEmpty => sendMailReportNotification(
            departementReports,
            department,
            None,
            taskDate.minusDays(7)
          )
          case _ =>
        }
      )}
    )
  }

  def runDailyNotificationTask(taskDate: LocalDate, category: Option[ReportCategory]) = {

    logger.debug(s"Traitement de notification quotidien des signalements - category ${category}")

    reportRepository.getReports(
        0,
        10000,
        ReportFilter(
          start = Some(taskDate.minusDays(1)),
          end = Some(taskDate),
          category = category.map(_.value)
        )
    ).map(reports => {
      departments.foreach(department =>
        reports.entities.filter(report => report.companyPostalCode.map(_.startsWith(department)).getOrElse(false)) match {
          case departementReports if departementReports.nonEmpty => sendMailReportNotification(
            departementReports,
            department,
            category,
            taskDate.minusDays(1)
          )
          case _ =>
        }
      )}
    )
  }

  private def sendMailReportNotification(reports: Seq[Report], department: String, category: Option[ReportCategory], startDate: LocalDate) = {

    subscriptionRepository.listSubscribeUserMails(department, category).flatMap(recipients => {

      logger.debug(s"Department $department - category ${category} - send mail to ${recipients}")
      logger.debug(s"reports $reports")

      Future(mailerService.sendEmail(
        from = configuration.get[EmailAddress]("play.mail.from"),
        recipients = Seq.empty,
        blindRecipients = recipients,
        subject = s"[SignalConso] ${
          reports.length match {
            case 0 => "Aucun nouveau signalement"
            case 1 => "Un nouveau signalement"
            case n => s"${reports.length} nouveaux signalements"
          }
        } ${category.map(c => s"dans la catégorie ${c.value} ").getOrElse("")}pour le département ${department}",
        bodyHtml = views.html.mails.dgccrf.reportNotification(reports, department, category, startDate).toString,
        attachments = null
      ))
    })
  }
}
