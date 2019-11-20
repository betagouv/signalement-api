package tasks


import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, LocalTime, OffsetDateTime}
import java.util.UUID

import akka.actor.ActorSystem
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.Event._
import models._
import play.api.libs.mailer.AttachmentFile
import play.api.{Configuration, Environment, Logger}
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
                             s3Service: S3Service,
                             val silhouette: Silhouette[AuthEnv],
                             val silhouetteAPIKey: Silhouette[APIKeyEnv],
                             configuration: Configuration,
                             environment: Environment)
                            (implicit val executionContext: ExecutionContext) {


  val logger: Logger = Logger(this.getClass)

  val startTime = LocalTime.of(configuration.get[Int]("play.tasks.reminder.start.hour"), configuration.get[Int]("play.tasks.reminder.start.minute"), 0)
  val interval = configuration.get[Int]("play.tasks.reminder.intervalInHours").hours
  val reportExpirationDelay = java.time.Period.parse(configuration.get[String]("play.reports.expirationDelay"))

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
      onGoingReportsPostReminders <- Future.sequence(
        extractOnGoingReportsToRemindByPost(onGoingReportsWithAdmins, reportEventsMap, now)
          .map(reportWithAdmins => remindOnGoingReportByPost(reportWithAdmins._1))
      )
      closedByNoReadingForUserWithoutEmail <- Future.sequence(
        extractOnGoingReportsToCloseByNoReadingForUserWithoutEmail(onGoingReportsWithAdmins, reportEventsMap, now)
          .map(reportWithAdmins => closeOnGoingReportByNoReadingForUserWithoutEmail(reportWithAdmins._1))
      )
      onGoingReportsMailReminders <- Future.sequence(
        extractReportsToRemindByMail(onGoingReportsWithAdmins, reportEventsMap, now, CONTACT_EMAIL)
          .map(reportWithAdmins => remindReportByMail(reportWithAdmins._1, reportWithAdmins._2.flatMap(_.email)))
      )
      closedByNoReadingForUserWithEmail <- Future.sequence(
        extractReportsToCloseForUserWithEmail(onGoingReportsWithAdmins, reportEventsMap, now)
          .map(reportWithAdmins => closeOnGoingReportByNoReadingForUserWithEmail(reportWithAdmins._1))
      )
      transmittedReportsMailReminders <- Future.sequence(
        extractReportsToRemindByMail(transmittedReportsWithAdmins, reportEventsMap, now, ENVOI_SIGNALEMENT)
          .map(reportWithAdmins => remindReportByMail(reportWithAdmins._1, reportWithAdmins._2.flatMap(_.email)))
      )
      closedByNoAction <- Future.sequence(
        extractReportsToCloseForUserWithEmail(transmittedReportsWithAdmins, reportEventsMap, now)
          .map(reportWithAdmins => closeTransmittedReportByNoAction(reportWithAdmins._1))
      )
    } yield {
      (onGoingReportsPostReminders ::: closedByNoReadingForUserWithEmail :::
        onGoingReportsMailReminders ::: closedByNoReadingForUserWithoutEmail :::
        transmittedReportsMailReminders ::: closedByNoAction).map(
        reminder => logger.debug(s"Relance [${reminder.reportId} - ${reminder.value}]")
      )
    }
  }


  def extractEventsWithAction(reportId: UUID, reportEventsMap: Map[UUID, List[Event]], action: ActionEventValue): List[Event] = {
    reportEventsMap.getOrElse(reportId, List.empty).filter(_.action == action)
  }

  def extractOnGoingReportsToRemindByPost(reportsWithadmins: List[(Report, List[User])], reportEventsMap: Map[UUID, List[Event]], now: LocalDateTime) = {
    reportsWithadmins
      .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id.get, reportEventsMap, RELANCE).length == 0)
      .filterNot(reportWithAdmins => reportWithAdmins._2.exists(_.email.isDefined))
      .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id.get, reportEventsMap, CONTACT_COURRIER)
        .headOption.flatMap(_.creationDate).map(_.toLocalDateTime.isBefore(now.minus(reportExpirationDelay))).getOrElse(false))
  }

  def remindOnGoingReportByPost(report: Report) = {
    for {
      newEvent <- eventRepository.createEvent(generateReminderEvent(report))
      _ <- reportRepository.update(report.copy(status = Some(A_TRAITER)))
    } yield {
      Reminder(report.id.get, ReminderValue.RemindOnGoingReportByPost)
    }
  }

  def extractOnGoingReportsToCloseByNoReadingForUserWithoutEmail(reportsWithAdmins: List[(Report, List[User])], reportEventsMap: Map[UUID, List[Event]], now: LocalDateTime) = {
    reportsWithAdmins
      .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id.get, reportEventsMap, RELANCE)
        .headOption.flatMap(_.creationDate).map(_.toLocalDateTime.isBefore(now.minus(reportExpirationDelay))).getOrElse(false))
      .filterNot(reportWithAdmins => reportWithAdmins._2.exists(_.email.isDefined))
  }

  def closeOnGoingReportByNoReadingForUserWithoutEmail(report: Report) = {
    for {
      newEvent <- eventRepository.createEvent(generateNoReadingEvent(report))
      _ <- reportRepository.update(report.copy(status = Some(SIGNALEMENT_NON_CONSULTE)))
    } yield {
      Reminder(report.id.get, ReminderValue.CloseOnGoingReportByNoReadingForUserWithoutEmail)
    }
  }

  def extractReportsToRemindByMail(reportsWithAdmins: List[(Report, List[User])], reportEventsMap: Map[UUID, List[Event]], now: LocalDateTime, previousAction: ActionEventValue) = {
    reportsWithAdmins
      .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id.get, reportEventsMap, RELANCE).length == 0)
      .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email.isDefined))
      .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id.get, reportEventsMap, previousAction)
        .headOption.flatMap(_.creationDate).map(_.toLocalDateTime.isBefore(now.minusDays(7))).getOrElse(false)) :::
      reportsWithAdmins
        .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id.get, reportEventsMap, RELANCE).length == 1)
        .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email.isDefined))
        .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id.get, reportEventsMap, RELANCE)
          .head.creationDate.map(_.toLocalDateTime.isBefore(now.minusDays(7))).getOrElse(false))
  }

  def remindReportByMail(report: Report, adminMails: List[EmailAddress]) = {
    val expirationDate = report.creationDate.get.plus(reportExpirationDelay)
    eventRepository.createEvent(generateReminderEvent(report)).map { newEvent =>
      mailerService.sendEmail(
        from = EmailAddress(configuration.get[String]("play.mail.from")),
        recipients = adminMails: _*)(
        subject = "Nouveau signalement",
        bodyHtml = views.html.mails.professional.reportReminder(report, expirationDate).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      )
      Reminder(report.id.get, ReminderValue.RemindReportByMail)
    }
  }

  def extractReportsToCloseForUserWithEmail(reportsWithAdmins: List[(Report, List[User])], reportEventsMap: Map[UUID, List[Event]], now: LocalDateTime) = {
    reportsWithAdmins
      .filter(reportWithAdmins => extractEventsWithAction(reportWithAdmins._1.id.get, reportEventsMap, RELANCE)
        .filter(_.creationDate.map(_.toLocalDateTime.isBefore(now.minusDays(7))).getOrElse(false)).length == 2)
      .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email.isDefined))
  }

  def closeOnGoingReportByNoReadingForUserWithEmail(report: Report) = {
    for {
      newEvent <- eventRepository.createEvent(generateNoReadingEvent(report))
      _ <- reportRepository.update(report.copy(status = Some(SIGNALEMENT_NON_CONSULTE)))
    } yield {
      mailerService.sendEmail(
        from = EmailAddress(configuration.get[String]("play.mail.from")),
        recipients = report.email)(
        subject = "Le professionnel n’a pas souhaité consulter votre signalement",
        bodyHtml = views.html.mails.consumer.reportClosedByNoReading(report).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      )
      Reminder(report.id.get, ReminderValue.CloseOnGoingReportByNoReadingForUserWithEmail)
    }
  }

  def closeTransmittedReportByNoAction(report: Report) = {
    for {
      newEvent <- eventRepository.createEvent(generateReadingNoActionEvent(report))
      _ <- reportRepository.update(report.copy(status = Some(SIGNALEMENT_CONSULTE_IGNORE)))
    } yield {
      mailerService.sendEmail(
        from = EmailAddress(configuration.get[String]("play.mail.from")),
        recipients = report.email)(subject = "Le professionnel n’a pas répondu au signalement",
        bodyHtml = views.html.mails.consumer.reportClosedByNoAction(report).toString,
        attachments = Seq(
          AttachmentFile("logo-signal-conso.png", environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))
        )
      )
      Reminder(report.id.get, ReminderValue.CloseTransmittedReportByNoAction)
    }
  }


  private def generateReminderEvent(report: Report): Event = Event(
    Some(UUID.randomUUID()),
    report.id,
    None,
    Some(OffsetDateTime.now()),
    PRO,
    RELANCE,
    stringToDetailsJsValue(s"Ajout d'un évènement de relance")
  )

  private def generateNoReadingEvent(report: Report): Event = Event(
    Some(UUID.randomUUID()),
    report.id,
    None,
    Some(OffsetDateTime.now()),
    PRO,
    NON_CONSULTE,
    stringToDetailsJsValue("Clôture automatique : signalement non consulté")
  )

  private def generateReadingNoActionEvent(report: Report): Event = Event(
    Some(UUID.randomUUID()),
    report.id,
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
    CloseOnGoingReportByNoReadingForUserWithoutEmail,
    RemindReportByMail,
    CloseOnGoingReportByNoReadingForUserWithEmail,
    CloseTransmittedReportByNoAction= Value
  }
}
