package tasks.report

import config.TaskConfiguration
import models.event.Event.stringToDetailsJsValue
import models.User
import models.event.Event
import models.report.Report
import play.api.Logger
import repositories.event.EventRepositoryInterface
import services.Email.ProReportUnreadReminder
import services.MailService
import tasks.model.TaskType
import tasks.report.ReportTask.extractEventsWithReportIdAndAction
import tasks.TaskExecutionResult
import tasks.toValidated
import utils.Constants.ActionEvent.EMAIL_PRO_NEW_REPORT
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_READING
import utils.Constants.EventType.SYSTEM
import utils.EmailAddress

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UnreadReportsReminderTask(
    taskConfiguration: TaskConfiguration,
    eventRepository: EventRepositoryInterface,
    emailService: MailService
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  import taskConfiguration.report.mailReminderDelay

  def sendUnreadReportReminderEmail(
      unreadReportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      todayAtStartOfDay: LocalDateTime
  ): Future[List[TaskExecutionResult]] = Future.sequence(
    filterReportsToRemindByMail(unreadReportsWithAdmins, reportEventsMap, todayAtStartOfDay)
      .map { case (report, users) =>
        remindUnreadReportByMail(report, users.map(_.email), reportEventsMap)
      }
  )

  private def filterReportsToRemindByMail(
      unreadReportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      todayAtStartOfDay: LocalDateTime
  ): List[(Report, List[User])] = {

    val reportWithNoReminder: List[(Report, List[User])] = unreadReportsWithAdmins
      // Keep only the reports for which we never sent this email reminder yet
      .filter { case (report, _) =>
        extractEventsWithReportIdAndAction(reportEventsMap, report.id, EMAIL_PRO_REMIND_NO_READING).isEmpty
      }
      // For which have an admin account
      .filter { case (_, admin) => admin.exists(_.email.nonEmpty) }
      // For which we sent the email "Nouveau signalement" at least 7 days ago
      .filter { case (report, _) =>
        extractEventsWithReportIdAndAction(reportEventsMap, report.id, EMAIL_PRO_NEW_REPORT).headOption
          .map(_.creationDate)
          .getOrElse(report.creationDate)
          .toLocalDateTime
          .isBefore(todayAtStartOfDay.minusDays(7))
      }

    val reportWithOneReminder: List[(Report, List[User])] = unreadReportsWithAdmins
      // Keep only the reports for which we sent this email reminder exactly once
      .filter { case (report, _) =>
        extractEventsWithReportIdAndAction(
          reportEventsMap,
          report.id,
          EMAIL_PRO_REMIND_NO_READING
        ).length == 1
      }
      // For which have an admin account
      .filter { case (_, admin) => admin.exists(_.email.nonEmpty) }
      // Keep only the reports for which this email reminder was sent at least 7 days ago
      .filter { case (report, _) =>
        extractEventsWithReportIdAndAction(
          reportEventsMap,
          report.id,
          EMAIL_PRO_REMIND_NO_READING
        ).head.creationDate.toLocalDateTime.isBefore(todayAtStartOfDay.minusDays(7))
      }

    reportWithNoReminder ::: reportWithOneReminder
  }

  private def remindUnreadReportByMail(
      report: Report,
      adminMails: List[EmailAddress],
      reportEventsMap: Map[UUID, List[Event]]
  ) = {

    val reportExpirationDate = ReportTask.computeReportExpirationDate(
      mailReminderDelay,
      report.id,
      reportEventsMap,
      EMAIL_PRO_REMIND_NO_READING
    )
    val taskExecution: Future[Unit] = {
      logger.debug(s"Sending email")
      for {
        _ <- emailService.send(ProReportUnreadReminder(adminMails, report, reportExpirationDate))
        _ <- eventRepository.create(
          Event(
            UUID.randomUUID(),
            Some(report.id),
            report.companyId,
            None,
            OffsetDateTime.now(),
            SYSTEM,
            EMAIL_PRO_REMIND_NO_READING,
            stringToDetailsJsValue(s"Relance envoyée à ${adminMails.mkString(", ")}")
          )
        )
      } yield ()
    }

    toValidated(taskExecution, report.id, TaskType.RemindUnreadReportsByEmail)
  }

}
