package tasks

import config.AppConfigLoader
import models.Event.stringToDetailsJsValue
import models.Event
import models.Report
import models.ReportStatus
import models.User
import play.api.Logger
import repositories.EventRepository
import repositories.ReportRepository
import services.Email.ConsumerReportClosedNoAction
import services.MailService
import tasks.ReportTask.MaxReminderCount
import tasks.ReportTask.extractEventsWithAction
import tasks.model.TaskOutcome.FailedTask
import tasks.model.TaskOutcome.SuccessfulTask
import tasks.model.TaskOutcome
import tasks.model.TaskType
import utils.Constants.ActionEvent.EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_ACTION
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.EventType.CONSO
import utils.Constants.EventType.SYSTEM

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class NoActionReportsCloseTask @Inject() (
    appConfigLoader: AppConfigLoader,
    eventRepository: EventRepository,
    reportRepository: ReportRepository,
    emailService: MailService
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  val noAccessReadingDelay = appConfigLoader.get.report.noAccessReadingDelay
  val mailReminderDelay = appConfigLoader.get.report.mailReminderDelay

  /** Close reports that have no response after MaxReminderCount sent
    * @param readReportsWithAdmins
    *   List of all read reports with eventual associated users
    * @param startingPoint
    *   starting point to compute range
    * @return
    *   Unread reports
    */
  def closeNoAction(
      readReportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      startingPoint: LocalDateTime
  ): Future[List[TaskOutcome]] = Future.sequence(
    extractTransmittedWithAccessReports(readReportsWithAdmins, reportEventsMap, startingPoint)
      .map(reportWithAdmins => closeTransmittedReportByNoAction(reportWithAdmins._1))
  )

  private def extractTransmittedWithAccessReports(
      reportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      now: LocalDateTime
  ): List[(Report, List[User])] =
    reportsWithAdmins
      .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email.nonEmpty))
      .filter(reportWithAdmins =>
        extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_REMIND_NO_ACTION)
          .count(_.creationDate.exists(_.toLocalDateTime.isBefore(now.minus(mailReminderDelay)))) == MaxReminderCount
      )

  private def closeTransmittedReportByNoAction(report: Report): Future[TaskOutcome] = {
    val successfulTaskOrError: Future[SuccessfulTask] = for {
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
      _ <- reportRepository.update(report.copy(status = ReportStatus.ConsulteIgnore))
      _ <- emailService.send(ConsumerReportClosedNoAction(report))
    } yield SuccessfulTask(report.id, TaskType.CloseTransmittedReportByNoAction)

    successfulTaskOrError.recoverWith { case err =>
      logger.error("Error processing reminder task", err)
      Future.successful(FailedTask(report.id, TaskType.CloseTransmittedReportByNoAction, err))
    }

  }

}
