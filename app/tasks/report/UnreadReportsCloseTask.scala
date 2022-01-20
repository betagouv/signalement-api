package tasks.report

import config.TaskConfiguration
import models.Event.stringToDetailsJsValue
import models.Event
import models.Report
import models.ReportStatus
import models.User
import play.api.Logger
import repositories.EventRepository
import repositories.ReportRepository
import services.Email.ConsumerReportClosedNoReading
import services.MailService
import tasks.model.TaskOutcome
import tasks.model.TaskType
import tasks.report.ReportTask.MaxReminderCount
import tasks.report.ReportTask.extractEventsWithAction
import tasks.toTaskOutCome
import utils.Constants.ActionEvent.EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_READING
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_READING
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_READING
import utils.Constants.EventType.CONSO
import utils.Constants.EventType.SYSTEM

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UnreadReportsCloseTask @Inject() (
    taskConfiguration: TaskConfiguration,
    eventRepository: EventRepository,
    reportRepository: ReportRepository,
    emailService: MailService
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  val noAccessReadingDelay = taskConfiguration.report.noAccessReadingDelay
  val mailReminderDelay = taskConfiguration.report.mailReminderDelay

  /** Close all unread report ( especially those with no pro access) within noAccessReadingDelay var
    * @param onGoingReportsWithAdmins
    *   List of all unread reports with eventual associated users
    * @param startingPoint
    *   starting point to compute range
    * @return
    *   Unread reports
    */
  def closeUnread(
      onGoingReportsWithAdmins: List[(Report, List[User])],
      startingPoint: LocalDateTime
  ): Future[List[TaskOutcome]] = Future.sequence(
    extractAllUnreadReports(onGoingReportsWithAdmins, startingPoint)
      .map(reportWithAdmins => closeUnreadReport(reportWithAdmins._1))
  )

  def closeUnreadWithMaxReminderEventsSent(
      onGoingReportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      startingPoint: LocalDateTime
  ): Future[List[TaskOutcome]] =
    Future.sequence(
      extractUnreadWithAccessReports(onGoingReportsWithAdmins, reportEventsMap, startingPoint)
        .map(reportWithAdmins => closeUnreadReport(reportWithAdmins._1))
    )

  /** Extracts all unread report ( especially those with no pro access) within noAccessReadingDelay var
    * @param reportsWithAdmins
    *   List of all unread reports with eventual associated users
    * @param startingPoint
    *   starting point to compute range
    * @return
    *   Unread reports
    */
  private def extractAllUnreadReports(reportsWithAdmins: List[(Report, List[User])], startingPoint: LocalDateTime) =
    reportsWithAdmins
      .filterNot(reportWithAdmins => reportWithAdmins._2.exists(_.email.nonEmpty))
      .filter(reportWithAdmins =>
        reportWithAdmins._1.creationDate.toLocalDateTime.isBefore(startingPoint.minus(noAccessReadingDelay))
      )

  /** Extracts unread report that have MaxReminderCount reminder sent to associated pro user
    * @param reportsWithAdmins
    *   List of all unread reports with eventual associated users
    * @param reportEventsMap
    *   List of all reports events associated to reportsWithAdmins
    * @param now
    *   starting point to compute range
    * @return
    */
  private def extractUnreadWithAccessReports(
      reportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      now: LocalDateTime
  ): List[(Report, List[User])] =
    reportsWithAdmins
      .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email.nonEmpty))
      .filter(reportWithAdmins =>
        extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_REMIND_NO_READING)
          .count(_.creationDate.exists(_.toLocalDateTime.isBefore(now.minus(mailReminderDelay)))) == MaxReminderCount
      )

  private def closeUnreadReport(report: Report): Future[TaskOutcome] = {
    val taskExecution: Future[Unit] = for {
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
      _ <- reportRepository.update(report.copy(status = ReportStatus.NonConsulte))
      _ <- emailService.send(ConsumerReportClosedNoReading(report))
    } yield ()

    toTaskOutCome(taskExecution, report.id, TaskType.CloseUnreadReport)
  }
}
