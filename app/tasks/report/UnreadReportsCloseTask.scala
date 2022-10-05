package tasks.report

import cats.implicits.toTraverseOps
import config.TaskConfiguration
import models.event.Event.stringToDetailsJsValue
import models.User
import models.event.Event
import models.report.Report
import models.report.ReportStatus
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import services.Email.ConsumerReportClosedNoReading
import services.MailService
import tasks.model.TaskType
import tasks.report.ReportTask.MaxReminderCount
import tasks.report.ReportTask.extractEventsWithReportIdAndAction
import tasks.TaskExecutionResult
import tasks.toValidated
import utils.Constants.ActionEvent.EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_READING
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_READING
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_READING
import utils.Constants.EventType.CONSO
import utils.Constants.EventType.SYSTEM

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UnreadReportsCloseTask(
    taskConfiguration: TaskConfiguration,
    eventRepository: EventRepositoryInterface,
    reportRepository: ReportRepositoryInterface,
    companyRepository: CompanyRepositoryInterface,
    emailService: MailService
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  import taskConfiguration.report.noAccessReadingDelay
  import taskConfiguration.report.mailReminderDelay

  // Close reports created at least 60 days ago
  // and that do no have a Pro admin account
  def closeUnreadWithNoAdmin(
      unreadReportsWithAdmins: List[(Report, List[User])],
      todayAtStartOfDay: LocalDateTime
  ): Future[List[TaskExecutionResult]] = Future.sequence(
    unreadReportsWithAdmins
      // Remove the reports with at least an admin with an email
      // not sure why we check the email is non empty, in DB there seems to always be an email
      .filterNot { case (_, admins) => admins.exists(_.email.nonEmpty) }
      // Keep only reports created 60 days ago or more
      .filter { case (report, _) =>
        report.creationDate.toLocalDateTime.isBefore(todayAtStartOfDay.minus(noAccessReadingDelay))
      }
      .map(_._1)
      .map(closeUnreadReport)
  )

  def closeUnreadAndRemindedEnough(
      unreadReportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      todayAtStartOfDay: LocalDateTime
  ): Future[List[TaskExecutionResult]] =
    Future.sequence(
      unreadReportsWithAdmins
        // Keep the reports with at least an admin with an email
        .filter { case (_, admins) => admins.exists(_.email.nonEmpty) }
        // Keep the reports for which we sent exactly 2 emails de rappel "Signalement non consulté"
        // (looking only at the emails sent at least 7 days ago)
        .filter { case (report, _) =>
          extractEventsWithReportIdAndAction(reportEventsMap, report.id, EMAIL_PRO_REMIND_NO_READING)
            .count(
              _.creationDate.toLocalDateTime.isBefore(todayAtStartOfDay.minus(mailReminderDelay))
            ) == MaxReminderCount
        }
        .map(_._1)
        .map(closeUnreadReport)
    )

  private def closeUnreadReport(report: Report) = {
    val taskExecution: Future[Unit] = for {
      _ <- reportRepository.update(report.id, report.copy(status = ReportStatus.NonConsulte))
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          None,
          OffsetDateTime.now(),
          SYSTEM,
          REPORT_CLOSED_BY_NO_READING,
          stringToDetailsJsValue("Clôture automatique : signalement non consulté")
        )
      )
      maybeCompany <- report.companySiret.map(companyRepository.findBySiret(_)).flatSequence
      _ <- emailService.send(ConsumerReportClosedNoReading(report, maybeCompany))
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          None,
          OffsetDateTime.now(),
          CONSO,
          EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_READING
        )
      )
    } yield ()

    toValidated(taskExecution, report.id, TaskType.CloseUnreadReport)
  }
}
