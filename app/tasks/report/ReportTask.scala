package tasks.report

import akka.actor.ActorSystem
import config.SignalConsoConfiguration
import config.TaskConfiguration
import models._
import orchestrators.CompaniesVisibilityOrchestrator
import play.api.Logger
import repositories.EventRepository
import repositories.ReportRepository
import tasks.model.TaskOutcome
import tasks.model.TaskOutcome.FailedTask
import tasks.model.TaskOutcome.SuccessfulTask
import utils.Constants.ActionEvent._

import java.time._
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class ReportTask @Inject() (
    actorSystem: ActorSystem,
    reportRepository: ReportRepository,
    eventRepository: EventRepository,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    signalConsoConfiguration: SignalConsoConfiguration,
    unreadReportsReminderTask: UnreadReportsReminderTask,
    unreadReportsCloseTask: UnreadReportsCloseTask,
    readReportsReminderTask: ReadReportsReminderTask,
    noActionReportsCloseTask: NoActionReportsCloseTask,
    taskConfiguration: TaskConfiguration
)(implicit val executionContext: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  implicit val websiteUrl = signalConsoConfiguration.websiteURL
  implicit val timeout: akka.util.Timeout = 5.seconds

  val startTime = taskConfiguration.report.startTime
  val interval = taskConfiguration.report.intervalInHours

  val startDate =
    if (LocalTime.now.isAfter(startTime)) LocalDate.now.plusDays(1).atTime(startTime)
    else LocalDate.now.atTime(startTime)

  val initialDelay = (LocalDateTime.now.until(startDate, ChronoUnit.SECONDS) % (24 * 7 * 3600)).seconds

  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = initialDelay, interval = interval) { () =>
    logger.debug(s"initialDelay - ${initialDelay}");
    runTask(LocalDate.now.atStartOfDay())
  }

  def runTask(now: LocalDateTime) = {

    logger.info("Traitement de relance automatique")
    logger.info(s"taskDate - ${now}")

    val taskRanOrError: Future[List[TaskOutcome]] = for {

      unreadReportsWithAdmins <- getReportsWithAdminsByStatus(ReportStatus.TraitementEnCours)
      readReportsWithAdmins <- getReportsWithAdminsByStatus(ReportStatus.Transmis)

      reportEventsMap <- eventRepository.prefetchReportsEvents(
        (unreadReportsWithAdmins ::: readReportsWithAdmins).map(_._1)
      )
      _ = logger.info("Processing unread events")
      closedUnreadNoAccessReports <-
        unreadReportsCloseTask.closeUnread(unreadReportsWithAdmins, now)

      unreadReportsMailReminders <- unreadReportsReminderTask.sendReminder(
        unreadReportsWithAdmins,
        reportEventsMap,
        now
      )

      closedUnreadWithAccessReports <- unreadReportsCloseTask.closeUnreadWithMaxReminderEventsSent(
        unreadReportsWithAdmins,
        reportEventsMap,
        now
      )

      transmittedReportsMailReminders <- readReportsReminderTask.sendReminder(
        readReportsWithAdmins,
        reportEventsMap,
        now
      )
      closedByNoAction <- noActionReportsCloseTask.closeNoAction(readReportsWithAdmins, reportEventsMap, now)

      reminders = (closedUnreadWithAccessReports :::
        unreadReportsMailReminders ::: closedUnreadNoAccessReports :::
        transmittedReportsMailReminders ::: closedByNoAction)

      _ = logger.info("Successful reminders :")
      _ = reminders
        .filter(_.isInstanceOf[SuccessfulTask])
        .map(reminder => logger.debug(s"Relance pour [${reminder.reportId} - ${reminder.value}]"))

      _ = logger.info("Failed reminders :")
      _ = reminders
        .filter(_.isInstanceOf[FailedTask])
        .map(reminder => logger.warn(s"Failed report tasks [${reminder.reportId} - ${reminder.value}]"))

    } yield reminders

    taskRanOrError.recoverWith { case err =>
      logger.error(
        s"Unexpected failure, cannot run report task ( task date : $now, initialDelay : $initialDelay, startDate: $startDate )",
        err
      )
      Future.failed(err)
    }
  }

  private[this] def getReportsWithAdminsByStatus(status: ReportStatus): Future[List[(Report, List[User])]] =
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

}

object ReportTask {

  /** Max Reminder count on same reminder type
    */
  val MaxReminderCount = 2

  /** Compute the report expiration date. The time after the pro will not be able to respond to the report anymore
    * Dependending on the number of occurence of $action event already sent. For example if pro user h
    * @param reportId
    *   Report ID
    * @param reportEventsMap
    *   List of report events linked to provided report ID
    * @param action
    *   Event already sent for that report used to compute report expiration date
    * @return
    *   Report expiration date
    */
  private[tasks] def computeReportExpirationDate(
      mailReminderDelay: Period,
      reportId: UUID,
      reportEventsMap: Map[UUID, List[Event]],
      action: ActionEventValue
  ): OffsetDateTime =
    OffsetDateTime.now.plus(
      mailReminderDelay.multipliedBy(
        MaxReminderCount - extractEventsWithAction(reportId, reportEventsMap, action).length
      )
    )

  /** Extracts event from reportEventsMap depending on provided action & report ID
    * @param reportId
    *   Report ID
    * @param reportEventsMap
    *   List of report events linked to provided report ID
    * @param action
    *   Event action
    * @return
    *   Filtered events
    */
  def extractEventsWithAction(
      reportId: UUID,
      reportEventsMap: Map[UUID, List[Event]],
      action: ActionEventValue
  ): List[Event] =
    reportEventsMap.getOrElse(reportId, List.empty).filter(_.action == action)

}
