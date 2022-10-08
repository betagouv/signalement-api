package tasks.report

import akka.actor.ActorSystem
import cats.implicits._
import config.SignalConsoConfiguration
import config.TaskConfiguration
import models._
import models.event.Event
import models.event.Event.stringToDetailsJsValue
import models.report.Report
import models.report.ReportStatus
import models.report.ReportStatus.TraitementEnCours
import orchestrators.CompaniesVisibilityOrchestrator
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import services.Email.ConsumerReportClosedNoAction
import services.Email.ConsumerReportClosedNoReading
import services.Email.ProReportReadReminder
import services.Email.ProReportUnreadReminder
import services.MailService
import tasks.model.TaskType
import tasks.computeStartingTime
import tasks.toValidated
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ActionEvent.EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.ActionEvent.EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_READING
import utils.Constants.ActionEvent.EMAIL_PRO_NEW_REPORT
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_ACTION
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_READING
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_READING
import utils.Constants.EventType.CONSO
import utils.Constants.EventType.SYSTEM
import utils.EmailAddress

import java.time._
import java.time.temporal.TemporalAdjuster
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class MyNewReportClosureAndRemindersTask(
    // TODO cleaner truc inutiles
    actorSystem: ActorSystem,
    reportRepository: ReportRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    companyRepository: CompanyRepositoryInterface,
    emailService: MailService,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    signalConsoConfiguration: SignalConsoConfiguration,
    unreadReportsReminderTask: UnreadReportsReminderTask,
    unreadReportsCloseTask: UnreadReportsCloseTask,
    readReportsReminderTask: ReadReportsReminderTask,
    noActionReportsCloseTask: NoActionReportsCloseTask,
    taskConfiguration: TaskConfiguration
)(implicit val executionContext: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  // TODO check if useful

  implicit val timeout: akka.util.Timeout = 5.seconds

  val startTime: LocalTime = taskConfiguration.report.startTime
  val initialDelay: FiniteDuration = computeStartingTime(startTime)

  val interval: FiniteDuration = taskConfiguration.report.intervalInHours

  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = initialDelay, interval = interval) { () =>
    logger.debug(s"initialDelay - ${initialDelay}");
    if (taskConfiguration.active) {
      runTask()
    }
    ()
  }

  def runTask() = {

    val zoneParis = ZoneId.of("Europe/Paris")
    val todayAtStartOfDay =
      OffsetDateTime.now.atZoneSameInstant(zoneParis).`with`(LocalTime.MIN).toOffsetDateTime

    logger.info("Traitement de fermeture/relance pour chaque signalement")
    logger.info(s"taskDate - ${todayAtStartOfDay}")

    val ongoingReportsStatus = List(ReportStatus.TraitementEnCours, ReportStatus.Transmis)
    for {
      ongoingReportsWithUsers <- getReportsByStatusWithUsers(ongoingReportsStatus)
      ongoingReports = ongoingReportsWithUsers.map(_._1)
      eventsByReportId <- eventRepository.fetchEventsOfReports(ongoingReports)
    } yield ongoingReportsWithUsers.map { case (report, users) =>
      val expirationDateIsPassed = report.expirationDate.isAfter(todayAtStartOfDay)
      if (expirationDateIsPassed) {
        closeExpiredReport(report)
      } else if (users.nonEmpty) {
        val maxReminderCount = 2
        val reminderEmailsActions = List(EMAIL_PRO_REMIND_NO_READING, EMAIL_PRO_REMIND_NO_ACTION)
        val allEmailsToProActions = reminderEmailsActions :+ EMAIL_PRO_NEW_REPORT
        val previousEmailsEvents =
          eventsByReportId
            .getOrElse(report.id, Nil)
            .filter(e => allEmailsToProActions.contains(e.action))
        val latestEmailDate = previousEmailsEvents.map(_.creationDate).sorted.lastOption
        val hadMaxReminderEmails =
          previousEmailsEvents.count(e => reminderEmailsActions.contains(e.action)) > maxReminderCount
        // 7 days, but in practice, since we require 7 full days between the previous email and the next one,
        // the email will fire at J+8
        val delaysBetweenEmails = taskConfiguration.report.mailReminderDelay
        val latestEmailIsNotTooRecent = latestEmailDate.exists(_.isAfter(todayAtStartOfDay.minus(delaysBetweenEmails)))
        if (!hadMaxReminderEmails && latestEmailIsNotTooRecent) {
          sendReminderEmail(report, users)
        }

        // TODO terminer la gestion de la task, du future, de l'execution result etc. puis nettoyer la classe
      }
    }

    val executedTasksOrError = for {

      unreadReportsWithAdmins <- getReportsByStatusWithUsers(ReportStatus.TraitementEnCours)
      readNoActionReportsWithAdmins <- getReportsByStatusWithUsers(ReportStatus.Transmis)

      reportEventsMap <- eventRepository.fetchEventsOfReports(
        (unreadReportsWithAdmins ++ readNoActionReportsWithAdmins).map(_._1)
      )
      _ = logger.info("Processing unread events")
      closedUnreadNoAdmin <- unreadReportsCloseTask.closeUnreadWithNoAdmin(
        unreadReportsWithAdmins,
        todayAtStartOfDay
      )

      unreadReportsMailReminders <- unreadReportsReminderTask.sendUnreadReportReminderEmail(
        unreadReportsWithAdmins,
        reportEventsMap,
        todayAtStartOfDay
      )

      closedUnreadAndRemindedEnough <- unreadReportsCloseTask.closeUnreadAndRemindedEnough(
        unreadReportsWithAdmins,
        reportEventsMap,
        todayAtStartOfDay
      )

      transmittedReportsMailReminders <- readReportsReminderTask.sendReminder(
        readNoActionReportsWithAdmins,
        reportEventsMap,
        todayAtStartOfDay
      )
      closedByNoAction <- noActionReportsCloseTask.closeNoActionAndRemindedEnough(
        readNoActionReportsWithAdmins,
        reportEventsMap,
        todayAtStartOfDay
      )

      reminders = closedUnreadNoAdmin.sequence combine
        closedUnreadAndRemindedEnough.sequence combine
        unreadReportsMailReminders.sequence combine
        transmittedReportsMailReminders.sequence combine
        closedByNoAction.sequence

      _ = logger.info("Successful reminders :")
      _ = reminders
        .map(reminder => logger.debug(s"Relance pour [${reminder.mkString(",")}]"))

      _ = logger.info("Failed reminders :")
      _ = reminders
        .leftMap(_.map(reminder => logger.warn(s"Failed report tasks [${reminder._1} - ${reminder._2}]")))

    } yield reminders

    executedTasksOrError.recoverWith { case err =>
      logger.error(
        s"Unexpected failure, cannot run report task ( task date : $todayAtStartOfDay, initialDelay : $initialDelay )",
        err
      )
      Future.failed(err)
    }
  }

  private[this] def getReportsByStatusWithUsers(status: List[ReportStatus]): Future[List[(Report, List[User])]] =
    for {
      reports <- reportRepository.getByStatus(status)
      companiesSiretsAndIds = reports.flatMap(r =>
        for {
          siret <- r.companySiret
          id <- r.companyId
        } yield (siret, id)
      )
      usersByCompanyId <- companiesVisibilityOrchestrator.fetchUsersWithHeadOffices(companiesSiretsAndIds)
    } yield reports.flatMap(r => r.companyId.map(companyId => (r, usersByCompanyId.getOrElse(companyId, Nil))))

  private def wasReportRead(report: Report) = report.status == ReportStatus.TraitementEnCours

  private def closeExpiredReport(report: Report) = {
    val wasRead = report.status == ReportStatus.TraitementEnCours
    // TODO voir si on peut simplifier, ne mettre qu'un seul event, status, etc.
    // Historically the closure for read or unread reports was handled separately so we had different events, status, etc.
    // Now this distinction makes less sense. We kept the same system of events/status nonetheless.
    val (newStatus, closureEventAction, closureEventDetails, email, emailEventAction) = if (wasReportRead(report)) {
      (
        ReportStatus.ConsulteIgnore,
        REPORT_CLOSED_BY_NO_ACTION,
        "Clôture automatique : signalement consulté ignoré",
        ConsumerReportClosedNoAction,
        EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_ACTION
      )
    } else {
      (
        ReportStatus.NonConsulte,
        REPORT_CLOSED_BY_NO_READING,
        "Clôture automatique : signalement non consulté",
        ConsumerReportClosedNoReading,
        EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_READING
      )
    }
    val taskExecution: Future[Unit] = for {
      _ <- reportRepository.update(report.id, report.copy(status = newStatus))
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          None,
          OffsetDateTime.now(),
          SYSTEM,
          closureEventAction,
          stringToDetailsJsValue(closureEventDetails)
        )
      )
      maybeCompany <- report.companySiret.map(companyRepository.findBySiret).flatSequence
      _ <- emailService.send(email(report, maybeCompany))
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          None,
          OffsetDateTime.now(),
          CONSO,
          emailEventAction
        )
      )
    } yield ()
    toValidated(taskExecution, report.id, TaskType.CloseExpiredReport)
  }

  private def sendReminderEmail(
      report: Report,
      users: List[User]
  ) = {
    val emailAddresses = users.map(_.email)
    val (email, emailEventAction) = if (wasReportRead(report)) {
      (
        ProReportUnreadReminder,
        EMAIL_PRO_REMIND_NO_READING
      )
    } else {
      (
        ProReportReadReminder,
        EMAIL_PRO_REMIND_NO_ACTION
      )
    }
    val taskExecution: Future[Unit] = {
      logger.debug(s"Sending email")
      for {
        _ <- emailService.send(email(emailAddresses, report, report.expirationDate))
        _ <- eventRepository.create(
          // TODO faire un raccourci pour créer des events
          Event(
            UUID.randomUUID(),
            Some(report.id),
            report.companyId,
            None,
            OffsetDateTime.now(),
            SYSTEM,
            emailEventAction,
            stringToDetailsJsValue(s"Relance envoyée à ${emailAddresses.mkString(", ")}")
          )
        )
      } yield ()
    }
    toValidated(taskExecution, report.id, TaskType.RemindUnreadReportsByEmail)
  }
}
