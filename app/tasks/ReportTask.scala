package tasks

import java.time.temporal.ChronoUnit
import java.time.{DayOfWeek, LocalDate, LocalDateTime, LocalTime}

import akka.actor.ActorSystem
import javax.inject.Inject
import models.Report
import play.api.libs.mailer.AttachmentFile
import play.api.{Configuration, Environment, Logger}
import repositories.{ReportFilter, ReportRepository}
import services.MailerService
import utils.Constants.Departments

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ReportTask @Inject()(actorSystem: ActorSystem,
                           reportRepository: ReportRepository,
                           mailerService: MailerService,
                           configuration: Configuration,
                           environment: Environment)
                          (implicit executionContext: ExecutionContext) {

  val logger: Logger = Logger(this.getClass())

  val startTime = LocalTime.of(configuration.get[Int]("play.tasks.report.start.hour"), configuration.get[Int]("play.tasks.report.start.minute"), 0)
  val startDayOfWeek = DayOfWeek.valueOf(configuration.get[String]("play.tasks.report.start.dayOfWeek"))
  val interval = configuration.get[Int]("play.tasks.report.interval").days

  val startDate = LocalDate.now.atTime(startTime).plusDays(startDayOfWeek.getValue + 7 - LocalDate.now.getDayOfWeek.getValue)
  val initialDelay = (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds

  val mailsByDepartments = configuration.get[String]("play.tasks.report.mails")
    .split(";")
    .filter(mailsByDepartment => mailsByDepartment.split("=").length == 2)
    .map(mailsByDepartment => (mailsByDepartment.split("=")(0), mailsByDepartment.split("=")(1)))

  actorSystem.scheduler.schedule(initialDelay = initialDelay, interval = interval) {

    val taskDate = LocalDateTime.now

    Logger.debug(s"taskDate - ${taskDate}");
    Logger.debug(s"initialDelay - ${initialDelay}");
    Logger.debug(s"interval - ${interval}");

    val departments = Departments.AUTHORIZED

    reportRepository.getReports(
        0,
        10000,
        ReportFilter(departments = departments, start = Some(taskDate.minusDays(7)), end = Some(taskDate))
    ).map(reports =>
      departments.foreach(department =>
        sendMailReportsOfTheWeek(
          reports.entities.filter(report => report.companyPostalCode.map(_.substring(0, 2) == department).getOrElse(false)),
          department,
          taskDate.minusDays(7).toLocalDate)
      )
    )

  }

  private def sendMailReportsOfTheWeek(reports: Seq[Report], department: String, startDate: LocalDate) = {

    logger.debug(s"Department $department - send mail to ${getMailForDepartment(department)}")

    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = getMailForDepartment(department) :_*)(
      subject = s"[SignalConso] ${reports.length match {
        case 0 => "Aucun nouveau signalement"
        case 1 => "Un nouveau signalement"
        case n => s"${reports.length} nouveaux signalements"
      }} pour le département ${department}",
      bodyHtml = views.html.mails.reportOfTheWeek(reports, department, startDate).toString,
      attachments = Seq(
        AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
      )
    ))
  }

  private def getMailForDepartment(department: String) = {
    mailsByDepartments.find(mailByDepartment => mailByDepartment._1 == department).map(_._2.split(",").toList).getOrElse(Seq.empty)
  }
}
