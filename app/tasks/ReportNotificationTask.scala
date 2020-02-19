package tasks

import java.net.URI
import java.time.temporal.ChronoUnit
import java.time.{DayOfWeek, LocalDate, LocalDateTime, LocalTime}

import akka.actor.ActorSystem
import javax.inject.Inject
import models.Report
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

  val startTime = LocalTime.of(configuration.get[Int]("play.tasks.report.start.hour"), configuration.get[Int]("play.tasks.report.start.minute"), 0)
  val startDayOfWeek = DayOfWeek.valueOf(configuration.get[String]("play.tasks.report.start.dayOfWeek"))
  val interval = configuration.get[Int]("play.tasks.report.interval").days

  val startDate = LocalDate.now.atTime(startTime).plusDays(startDayOfWeek.getValue + 7 - LocalDate.now.getDayOfWeek.getValue)
  val initialDelay = (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds

  val departments = Departments.ALL

  actorSystem.scheduler.schedule(initialDelay = initialDelay, interval = interval) {
    logger.debug(s"initialDelay - ${initialDelay}");
    runTask(LocalDate.now)
  }

  def runTask(taskDate: LocalDate) = {

    logger.debug("Traitement de notification hebdomdaire des signalements")
    logger.debug(s"taskDate - ${taskDate}");

    reportRepository.getReports(
        0,
        10000,
        ReportFilter(departments = departments, start = Some(taskDate.minusDays(7)), end = Some(taskDate))
    ).map(reports =>{
      logger.debug(s"reports ${reports.entities.map(_.companyPostalCode)}")
      departments.foreach(department =>
        reports.entities.filter(report => report.companyPostalCode.map(_.substring(0, 2) == department).getOrElse(false)) match {
          case Nil =>
          case _ => sendMailReportsOfTheWeek(
            reports.entities.filter(report => report.companyPostalCode.map(_.substring(0, 2) == department).getOrElse(false)),
            department,
            taskDate.minusDays(7))
        }
      )}
    )

  }

  private def sendMailReportsOfTheWeek(reports: Seq[Report], department: String, startDate: LocalDate) = {

    subscriptionRepository.listSubscribeUserMailsForDepartment(department).flatMap(recipients => {

      logger.debug(s"Department $department - send mail to ${recipients}")

      Future(mailerService.sendEmail(
        from = configuration.get[EmailAddress]("play.mail.from"),
        recipients = recipients: _*)(
        subject = s"[SignalConso] ${
          reports.length match {
            case 0 => "Aucun nouveau signalement"
            case 1 => "Un nouveau signalement"
            case n => s"${reports.length} nouveaux signalements"
          }
        } pour le département ${department}",
        bodyHtml = views.html.mails.dgccrf.reportOfTheWeek(reports, department, startDate).toString
      ))
    })
  }
}
