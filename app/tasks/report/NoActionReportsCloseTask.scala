package tasks.report

import config.TaskConfiguration
import models.event.Event.stringToDetailsJsValue
import models.User
import play.api.Logger
import services.Email.ConsumerReportClosedNoAction
import services.MailService
import tasks.model.TaskType
import tasks.report.ReportTask.MaxReminderCount
import tasks.report.ReportTask.extractEventsWithReportIdAndAction
import tasks.TaskExecutionResult
import tasks.toValidated
import utils.Constants.ActionEvent.EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_ACTION
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.EventType.CONSO
import utils.Constants.EventType.SYSTEM
import cats.implicits._
import models.event.Event
import models.report.Report
import models.report.ReportStatus
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class NoActionReportsCloseTask(
    eventRepository: EventRepositoryInterface,
    reportRepository: ReportRepositoryInterface,
    companyRepository: CompanyRepositoryInterface,
    emailService: MailService,
    taskConfiguration: TaskConfiguration
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  import taskConfiguration.report.mailReminderDelay

  // Close the reports that have been read by the pro but not replied to
  // and for which we sent the reminder 2 times at least
  // (This means that after reading the report, the pro gets 3 * 7 days to reply)
  def closeNoActionAndRemindedEnough(
      readNoActionReportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      todayAtStartOfDay: LocalDateTime
  ): Future[List[TaskExecutionResult]] = Future
    .sequence(
      readNoActionReportsWithAdmins
        // Keep the reports with at least an admin
        .filter { case (_, admin) => admin.exists(_.email.nonEmpty) }
        // Keep the reports for which the email reminder "Signalement en attente de réponse" was sent exactly 2 times
        // (looking only at emails sent at least 7 days ago)
        .filter { case (report, _) =>
          extractEventsWithReportIdAndAction(reportEventsMap, report.id, EMAIL_PRO_REMIND_NO_ACTION)
            .count(
              _.creationDate.toLocalDateTime.isBefore(todayAtStartOfDay.minus(mailReminderDelay))
            ) == MaxReminderCount
        }
        .map(_._1)
        .map(closeTransmittedReportByNoAction)
    )

  private def closeTransmittedReportByNoAction(report: Report) = {
    val taskExecution: Future[Unit] = for {
      _ <- reportRepository.update(report.id, report.copy(status = ReportStatus.ConsulteIgnore))
      maybeCompany <- report.companySiret.map(companyRepository.findBySiret).flatSequence
      _ <- eventRepository.create(
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
      _ <- emailService.send(ConsumerReportClosedNoAction(report, maybeCompany))
      _ <- eventRepository.create(
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
    } yield ()
    toValidated(taskExecution, report.id, TaskType.CloseReadReportWithNoAction)

  }

}
