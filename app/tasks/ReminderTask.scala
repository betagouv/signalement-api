package tasks


import java.net.URI
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, LocalTime, OffsetDateTime}
import java.util.UUID

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.pattern.ask
import actors.EmailActor
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.{Inject, Named}
import models.Event._
import models._
import play.api.{Configuration, Logger}
import repositories.{EventRepository, ReportRepository, UserRepository}
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.EventType.PRO
import utils.Constants.ReportStatus._
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv
import utils.EmailAddress

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


class ReminderTask @Inject()(actorSystem: ActorSystem,
                             reportRepository: ReportRepository,
                             eventRepository: EventRepository,
                             userRepository: UserRepository,
                             mailerService: MailerService,
                             @Named("email-actor") emailActor: ActorRef,
                             s3Service: S3Service,
                             val silhouette: Silhouette[AuthEnv],
                             val silhouetteAPIKey: Silhouette[APIKeyEnv],
                             configuration: Configuration)
                            (implicit val executionContext: ExecutionContext) {


  val logger: Logger = Logger(this.getClass)

  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val timeout: akka.util.Timeout = 5.seconds

  val startTime = LocalTime.of(configuration.get[Int]("play.tasks.reminder.start.hour"), configuration.get[Int]("play.tasks.reminder.start.minute"), 0)
  val interval = configuration.get[Int]("play.tasks.reminder.intervalInHours").hours
  val noAccessReadingDelay = java.time.Period.parse(configuration.get[String]("play.reports.noAccessReadingDelay"))
  val mailReminderDelay = java.time.Period.parse(configuration.get[String]("play.reports.mailReminderDelay"))

  val startDate = if (LocalTime.now.isAfter(startTime)) LocalDate.now.plusDays(1).atTime(startTime) else LocalDate.now.atTime(startTime)
  val initialDelay = (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds

  actorSystem.scheduler.schedule(initialDelay = initialDelay, interval = interval) {
    logger.debug(s"initialDelay - ${initialDelay}");
    runTask(LocalDate.now.atStartOfDay())
  }

  def runTask(now: LocalDateTime) = {

    logger.debug("Traitement de relance automatique")
    logger.debug(s"taskDate - ${now}");

    for {
      onGoingReportsWithAdmins <- reportRepository.getReportsForStatusWithAdmins(TRAITEMENT_EN_COURS)
      transmittedReportsWithAdmins <- reportRepository.getReportsForStatusWithAdmins(SIGNALEMENT_TRANSMIS)
      reportEventsMap <- eventRepository.prefetchReportsEvents(onGoingReportsWithAdmins.map(_._1) ::: transmittedReportsWithAdmins.map(_._1))
      closedUnreadNoAccessReports <- Future.sequence(
        extractUnreadNoAccessReports(onGoingReportsWithAdmins, now)
          .map(reportWithAdmins => closeUnreadReport(reportWithAdmins._1))
      )
      onGoingReportsMailReminders <- Future.sequence(
        extractReportsToRemindByMail(onGoingReportsWithAdmins, reportEventsMap, now, CONTACT_EMAIL)
          .map(reportWithAdmins => remindReportByMail(reportWithAdmins._1, reportWithAdmins._2.map(_.email), reportEventsMap))
      )
      closedUnreadWithAccessReports <- Future.sequence(
        extractUnreadWithAccessReports(onGoingReportsWithAdmins, reportEventsMap, now)
          .map(reportWithAdmins => closeUnreadReport(reportWithAdmins._1))
      )
      transmittedReportsMailReminders <- Future.sequence(
        extractReportsToRemindByMail(transmittedReportsWithAdmins, reportEventsMap, now, ENVOI_SIGNALEMENT)
          .map(reportWithAdmins => remindReportByMail(reportWithAdmins._1, reportWithAdmins._2.map(_.email), reportEventsMap))
      )
      closedByNoAction <- Future.sequence(
        extractUnreadWithAccessReports(transmittedReportsWithAdmins, reportEventsMap, now)
          .map(reportWithAdmins => closeTransmittedReportByNoAction(reportWithAdmins._1))
      )
    } yield {
      (closedUnreadWithAccessReports :::
        onGoingReportsMailReminders ::: closedUnreadNoAccessReports :::
        transmittedReportsMailReminders ::: closedByNoAction).map(
        reminder => logger.debug(s"Relance [${reminder.reportId} - ${reminder.value}]")
      )
    }
  }


  def extractEventsWithAction(reportId: UUID, reportEventsMap: Map[UUID, List[Event]], action: ActionEventValue): List[Event] = {
    reportEventsMap.getOrElse(reportId, List.empty).filter(_.action == action)
  }

  def extractUnreadNoAccessReports(reportsWithAdmins: List[(Report, List[User])], now: LocalDateTime) = {
    reportsWithAdmins
      .filterNot(reportWithAdmins => reportWithAdmins._2.exists(_.email != EmailAddress("")))
      .filter(reportWithAdmins => reportWithAdmins._1.creationDate.toLocalDateTime.isBefore(now.minus(noAccessReadingDelay)))
  }

  def extractReportsToRemindByMail(reportsWithAdmins: List[(Report, List[User])], reportEventsMap: Map[UUID, List[Event]], now: LocalDateTime, previousAction: ActionEventValue) = {
    reportsWithAdmins
      .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, RELANCE).length == 0)
      .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email != EmailAddress("")))
      .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, previousAction)
        .headOption.flatMap(_.creationDate).getOrElse(reportWithAdmins._1.creationDate).toLocalDateTime.isBefore(now.minusDays(7))) :::
      reportsWithAdmins
        .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, RELANCE).length == 1)
        .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email != EmailAddress("")))
        .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, RELANCE)
          .head.creationDate.map(_.toLocalDateTime.isBefore(now.minusDays(7))).getOrElse(false))
  }

  def remindReportByMail(report: Report, adminMails: List[EmailAddress], reportEventsMap: Map[UUID, List[Event]]) = {
    val expirationDate = OffsetDateTime.now.plus(mailReminderDelay.multipliedBy(2 - extractEventsWithAction(report.id, reportEventsMap, RELANCE).length))
    eventRepository.createEvent(generateReminderEvent(report)).map { newEvent =>
      emailActor ? EmailActor.EmailRequest(
        from = configuration.get[EmailAddress]("play.mail.from"),
        recipients = adminMails,
        subject = "Nouveau signalement",
        bodyHtml = views.html.mails.professional.reportReminder(report, expirationDate).toString
      )
      Reminder(report.id, ReminderValue.RemindReportByMail)
    }
  }

  def extractUnreadWithAccessReports(reportsWithAdmins: List[(Report, List[User])], reportEventsMap: Map[UUID, List[Event]], now: LocalDateTime) = {
    reportsWithAdmins
      .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email != EmailAddress("")))
      .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, RELANCE)
        .filter(_.creationDate.map(_.toLocalDateTime.isBefore(now.minus(mailReminderDelay))).getOrElse(false)).length == 2)
  }

  def closeUnreadReport(report: Report) = {
    for {
      newEvent <- eventRepository.createEvent(generateNoReadingEvent(report))
      _ <- reportRepository.update(report.copy(status = SIGNALEMENT_NON_CONSULTE))
    } yield {
      emailActor ? EmailActor.EmailRequest(
        from = configuration.get[EmailAddress]("play.mail.from"),
        recipients = Seq(report.email),
        subject = "L'entreprise n'a pas souhaité consulter votre signalement",
        bodyHtml = views.html.mails.consumer.reportClosedByNoReading(report).toString,
        attachments = mailerService.attachmentSeqForWorkflowStepN(3)
      )
      Reminder(report.id, ReminderValue.CloseUnreadReport)
    }
  }

  def closeTransmittedReportByNoAction(report: Report) = {
    for {
      newEvent <- eventRepository.createEvent(generateReadingNoActionEvent(report))
      _ <- reportRepository.update(report.copy(status = SIGNALEMENT_CONSULTE_IGNORE))
    } yield {
      emailActor ? EmailActor.EmailRequest(
        from = configuration.get[EmailAddress]("play.mail.from"),
        recipients = Seq(report.email),
        subject = "L'entreprise n'a pas répondu au signalement",
        bodyHtml = views.html.mails.consumer.reportClosedByNoAction(report).toString,
        attachments = mailerService.attachmentSeqForWorkflowStepN(4)
      )
      Reminder(report.id, ReminderValue.CloseTransmittedReportByNoAction)
    }
  }


  private def generateReminderEvent(report: Report): Event = Event(
    Some(UUID.randomUUID()),
    Some(report.id),
    report.companyId,
    None,
    Some(OffsetDateTime.now()),
    PRO,
    RELANCE,
    stringToDetailsJsValue(s"Ajout d'un évènement de relance")
  )

  private def generateNoReadingEvent(report: Report): Event = Event(
    Some(UUID.randomUUID()),
    Some(report.id),
    report.companyId,
    None,
    Some(OffsetDateTime.now()),
    PRO,
    NON_CONSULTE,
    stringToDetailsJsValue("Clôture automatique : signalement non consulté")
  )

  private def generateReadingNoActionEvent(report: Report): Event = Event(
    Some(UUID.randomUUID()),
    Some(report.id),
    report.companyId,
    None,
    Some(OffsetDateTime.now()),
    PRO,
    CONSULTE_IGNORE,
    stringToDetailsJsValue("Clôture automatique : signalement consulté ignoré")
  )

  case class Reminder(
                     reportId: UUID,
                     value: ReminderValue.Value
                     )

  object ReminderValue extends Enumeration {
    val RemindOnGoingReportByPost,
    CloseUnreadReport,
    RemindReportByMail,
    CloseTransmittedReportByNoAction= Value
  }
}
