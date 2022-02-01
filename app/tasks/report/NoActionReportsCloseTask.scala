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
import services.Email.ConsumerReportClosedNoAction
import services.MailService
import tasks.model.TaskType
import tasks.report.ReportTask.MaxReminderCount
import tasks.report.ReportTask.extractEventsWithAction
import tasks.TaskExecutionResult
import tasks.toValidated
import utils.Constants.ActionEvent.EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_ACTION
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.EventType.CONSO
import utils.Constants.EventType.SYSTEM
import cats.implicits._

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class NoActionReportsCloseTask @Inject() (
    eventRepository: EventRepository,
    reportRepository: ReportRepository,
    emailService: MailService,
    taskConfiguration: TaskConfiguration
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  val noAccessReadingDelay = taskConfiguration.report.noAccessReadingDelay
  val mailReminderDelay = taskConfiguration.report.mailReminderDelay

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
  ): Future[List[TaskExecutionResult]] = Future
    .sequence(
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
          .count(_.creationDate.toLocalDateTime.isBefore(now.minus(mailReminderDelay))) == MaxReminderCount
      )

  private def closeTransmittedReportByNoAction(report: Report) = {
    val taskExecution: Future[Unit] = for {
      _ <- eventRepository.createEvent(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          None,
          OffsetDateTime.now(),
          SYSTEM,
          REPORT_CLOSED_BY_NO_ACTION,
          stringToDetailsJsValue("Clôture automatique : signalement consulté ignoré")
        )
      )
      _ <- eventRepository.createEvent(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          None,
          OffsetDateTime.now(),
          CONSO,
          EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_ACTION
        )
      )
      _ <- reportRepository.update(report.copy(status = ReportStatus.ConsulteIgnore))
      _ <- emailService.send(ConsumerReportClosedNoAction(report))
    } yield ()
    toValidated(taskExecution, report.id, TaskType.CloseReadReportWithNoAction)

  }

}