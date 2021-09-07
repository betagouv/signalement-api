package tasks

import akka.actor.ActorSystem
import com.mohiva.play.silhouette.api.Silhouette
import models.Event._
import models._
import orchestrators.CompaniesVisibilityOrchestrator
import play.api.Configuration
import play.api.Logger
import repositories.EventRepository
import repositories.ReportRepository
import services.MailService
import utils.Constants.ActionEvent._
import utils.Constants.EventType.CONSO
import utils.Constants.EventType.SYSTEM
import utils.Constants.ReportStatus._
import utils.EmailAddress
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv

import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class ReminderTask @Inject() (
    actorSystem: ActorSystem,
    reportRepository: ReportRepository,
    eventRepository: EventRepository,
    mailService: MailService,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    val silhouette: Silhouette[AuthEnv],
    val silhouetteAPIKey: Silhouette[APIKeyEnv],
    configuration: Configuration
)(implicit val executionContext: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  implicit val websiteUrl = configuration.get[URI]("play.website.url")
  implicit val timeout: akka.util.Timeout = 5.seconds

  val startTime = LocalTime.of(
    configuration.get[Int]("play.tasks.reminder.start.hour"),
    configuration.get[Int]("play.tasks.reminder.start.minute"),
    0
  )
  val interval = configuration.get[Int]("play.tasks.reminder.intervalInHours").hours
  val noAccessReadingDelay = java.time.Period.parse(configuration.get[String]("play.reports.noAccessReadingDelay"))
  val mailReminderDelay = java.time.Period.parse(configuration.get[String]("play.reports.mailReminderDelay"))

  val startDate =
    if (LocalTime.now.isAfter(startTime)) LocalDate.now.plusDays(1).atTime(startTime)
    else LocalDate.now.atTime(startTime)
  val initialDelay = (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds

  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = initialDelay, interval = interval) { () =>
    logger.debug(s"initialDelay - $initialDelay");
    runTask(LocalDate.now.atStartOfDay())
  }

  def runTask(now: LocalDateTime) = {

    logger.debug("Traitement de relance automatique")
    logger.debug(s"taskDate - $now");

    for {
      onGoingReportsWithAdmins <- getReportsWithAdminsByStatus(TRAITEMENT_EN_COURS)
      transmittedReportsWithAdmins <- getReportsWithAdminsByStatus(SIGNALEMENT_TRANSMIS)
      reportEventsMap <- eventRepository.prefetchReportsEvents(
        onGoingReportsWithAdmins.map(_._1) ::: transmittedReportsWithAdmins.map(_._1)
      )
      closedUnreadNoAccessReports <- Future.sequence(
        extractUnreadNoAccessReports(onGoingReportsWithAdmins, now)
          .map(reportWithAdmins => closeUnreadReport(reportWithAdmins._1))
      )
      onGoingReportsMailReminders <-
        Future.sequence(
          extractUnreadReportsToRemindByMail(onGoingReportsWithAdmins, reportEventsMap, now)
            .map(reportWithAdmins =>
              remindUnreadReportByMail(reportWithAdmins._1, reportWithAdmins._2.map(_.email), reportEventsMap)
            )
        )
      closedUnreadWithAccessReports <- Future.sequence(
        extractUnreadWithAccessReports(onGoingReportsWithAdmins, reportEventsMap, now)
          .map(reportWithAdmins => closeUnreadReport(reportWithAdmins._1))
      )
      transmittedReportsMailReminders <-
        Future.sequence(
          extractTransmittedReportsToRemindByMail(transmittedReportsWithAdmins, reportEventsMap, now)
            .map(reportWithAdmins =>
              remindTransmittedReportByMail(reportWithAdmins._1, reportWithAdmins._2.map(_.email), reportEventsMap)
            )
        )
      closedByNoAction <- Future.sequence(
        extractTransmittedWithAccessReports(transmittedReportsWithAdmins, reportEventsMap, now)
          .map(reportWithAdmins => closeTransmittedReportByNoAction(reportWithAdmins._1))
      )
    } yield (closedUnreadWithAccessReports :::
      onGoingReportsMailReminders ::: closedUnreadNoAccessReports :::
      transmittedReportsMailReminders ::: closedByNoAction).map(reminder =>
      logger.debug(s"Relance [${reminder.reportId} - ${reminder.value}]")
    )
  }

  private[this] def getReportsWithAdminsByStatus(status: ReportStatusValue): Future[List[(Report, List[User])]] =
    for {
      reports <- reportRepository.getByStatus(status)
      mapAdminsByCompanyId <- companiesVisibilityOrchestrator.fetchAdminsWithHeadOffices(
        reports.flatMap(c =>
          for {
            siret <- c.companySiret
            id <- c.companyId
          } yield (siret, id)
        )
      )
    } yield reports.flatMap(r => r.companyId.map(companyId => (r, mapAdminsByCompanyId.getOrElse(companyId, Nil))))

  def extractEventsWithAction(
      reportId: UUID,
      reportEventsMap: Map[UUID, List[Event]],
      action: ActionEventValue
  ): List[Event] =
    reportEventsMap.getOrElse(reportId, List.empty).filter(_.action == action)

  def extractUnreadNoAccessReports(reportsWithAdmins: List[(Report, List[User])], now: LocalDateTime) =
    reportsWithAdmins
      .filterNot(reportWithAdmins => reportWithAdmins._2.exists(_.email != EmailAddress("")))
      .filter(reportWithAdmins =>
        reportWithAdmins._1.creationDate.toLocalDateTime.isBefore(now.minus(noAccessReadingDelay))
      )

  def extractUnreadReportsToRemindByMail(
      reportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      now: LocalDateTime
  ) =
    reportsWithAdmins
      .filter(reportWithAdmins =>
        extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_REMIND_NO_READING).length == 0
      )
      .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email != EmailAddress("")))
      .filter(reportWithAdmins =>
        extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_NEW_REPORT).headOption
          .flatMap(_.creationDate)
          .getOrElse(reportWithAdmins._1.creationDate)
          .toLocalDateTime
          .isBefore(now.minusDays(7))
      ) :::
      reportsWithAdmins
        .filter(reportWithAdmins =>
          extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_REMIND_NO_READING).length == 1
        )
        .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email != EmailAddress("")))
        .filter(reportWithAdmins =>
          extractEventsWithAction(
            reportWithAdmins._1.id,
            reportEventsMap,
            EMAIL_PRO_REMIND_NO_READING
          ).head.creationDate.exists(_.toLocalDateTime.isBefore(now.minusDays(7)))
        )

  def remindUnreadReportByMail(
      report: Report,
      adminMails: List[EmailAddress],
      reportEventsMap: Map[UUID, List[Event]]
  ) = {
    val expirationDate = OffsetDateTime.now.plus(
      mailReminderDelay.multipliedBy(
        2 - extractEventsWithAction(report.id, reportEventsMap, EMAIL_PRO_REMIND_NO_READING).length
      )
    )
    eventRepository
      .createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          report.companyId,
          None,
          Some(OffsetDateTime.now()),
          SYSTEM,
          EMAIL_PRO_REMIND_NO_READING,
          stringToDetailsJsValue(s"Relance envoyée à ${adminMails.mkString(", ")}")
        )
      )
      .map { newEvent =>
        mailService.Pro.sendReportUnreadReminder(adminMails, report, expirationDate)
        Reminder(report.id, ReminderValue.RemindReportByMail)
      }
  }

  def extractTransmittedReportsToRemindByMail(
      reportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      now: LocalDateTime
  ) =
    reportsWithAdmins
      .filter(reportWithAdmins =>
        extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_REMIND_NO_ACTION).length == 0
      )
      .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email != EmailAddress("")))
      .filter(reportWithAdmins =>
        extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, REPORT_READING_BY_PRO).headOption
          .flatMap(_.creationDate)
          .getOrElse(reportWithAdmins._1.creationDate)
          .toLocalDateTime
          .isBefore(now.minusDays(7))
      ) :::
      reportsWithAdmins
        .filter(reportWithAdmins =>
          extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_REMIND_NO_ACTION).length == 1
        )
        .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email != EmailAddress("")))
        .filter(reportWithAdmins =>
          extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_REMIND_NO_ACTION).head.creationDate
            .exists(_.toLocalDateTime.isBefore(now.minusDays(7)))
        )

  def remindTransmittedReportByMail(
      report: Report,
      adminMails: List[EmailAddress],
      reportEventsMap: Map[UUID, List[Event]]
  ) = {
    val expirationDate = OffsetDateTime.now.plus(
      mailReminderDelay.multipliedBy(
        2 - extractEventsWithAction(report.id, reportEventsMap, EMAIL_PRO_REMIND_NO_ACTION).length
      )
    )
    eventRepository
      .createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          report.companyId,
          None,
          Some(OffsetDateTime.now()),
          SYSTEM,
          EMAIL_PRO_REMIND_NO_ACTION,
          stringToDetailsJsValue(s"Relance envoyée à ${adminMails.mkString(", ")}")
        )
      )
      .map { newEvent =>
        mailService.Pro.sendReportTransmittedReminder(adminMails, report, expirationDate)
        Reminder(report.id, ReminderValue.RemindReportByMail)
      }
  }

  def extractUnreadWithAccessReports(
      reportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      now: LocalDateTime
  ) =
    reportsWithAdmins
      .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email != EmailAddress("")))
      .filter(reportWithAdmins =>
        extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_REMIND_NO_READING)
          .count(_.creationDate.exists(_.toLocalDateTime.isBefore(now.minus(mailReminderDelay)))) == 2
      )

  def closeUnreadReport(report: Report) =
    for {
      _ <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          report.companyId,
          None,
          Some(OffsetDateTime.now()),
          SYSTEM,
          REPORT_CLOSED_BY_NO_READING,
          stringToDetailsJsValue("Clôture automatique : signalement non consulté")
        )
      )
      _ <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          report.companyId,
          None,
          Some(OffsetDateTime.now()),
          CONSO,
          EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_READING
        )
      )
      _ <- reportRepository.update(report.copy(status = SIGNALEMENT_NON_CONSULTE))
    } yield {
      mailService.Consumer.sendReportClosedByNoReading(report)
      Reminder(report.id, ReminderValue.CloseUnreadReport)
    }

  def extractTransmittedWithAccessReports(
      reportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      now: LocalDateTime
  ) =
    reportsWithAdmins
      .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email != EmailAddress("")))
      .filter(reportWithAdmins =>
        extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_REMIND_NO_ACTION)
          .count(_.creationDate.exists(_.toLocalDateTime.isBefore(now.minus(mailReminderDelay)))) == 2
      )

  def closeTransmittedReportByNoAction(report: Report) =
    for {
      _ <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          report.companyId,
          None,
          Some(OffsetDateTime.now()),
          SYSTEM,
          REPORT_CLOSED_BY_NO_ACTION,
          stringToDetailsJsValue("Clôture automatique : signalement consulté ignoré")
        )
      )
      _ <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          report.companyId,
          None,
          Some(OffsetDateTime.now()),
          CONSO,
          EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_ACTION
        )
      )
      _ <- reportRepository.update(report.copy(status = SIGNALEMENT_CONSULTE_IGNORE))
    } yield {
      mailService.Consumer.sendAttachmentSeqForWorkflowStepN(report)
      Reminder(report.id, ReminderValue.CloseTransmittedReportByNoAction)
    }

  case class Reminder(
      reportId: UUID,
      value: ReminderValue.Value
  )

  object ReminderValue extends Enumeration {
    val RemindOnGoingReportByPost, CloseUnreadReport, RemindReportByMail, CloseTransmittedReportByNoAction = Value
  }
}
