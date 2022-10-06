package tasks.report

import config.TaskConfiguration
import models.event.Event.stringToDetailsJsValue
import models.User
import models.event.Event
import models.report.Report
import play.api.Logger
import repositories.event.EventRepositoryInterface
import services.Email.ProReportReadReminder
import services.MailService
import tasks.model.TaskType
import tasks.report.ReportTask.MaxReminderCount
import tasks.report.ReportTask.extractEventsWithReportIdAndAction
import tasks.TaskExecutionResult
import tasks.toValidated
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_ACTION
import utils.Constants.ActionEvent.REPORT_READING_BY_PRO
import utils.Constants.EventType.SYSTEM
import utils.EmailAddress

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReadReportsReminderTask(
    taskConfiguration: TaskConfiguration,
    eventRepository: EventRepositoryInterface,
    emailService: MailService
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  val mailReminderDelay: Period = taskConfiguration.report.mailReminderDelay

  def sendReminder(
      readNoActionReportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      todayAtStartOfDay: LocalDateTime
  ): Future[List[TaskExecutionResult]] = Future.sequence(
    extractTransmittedReportsToRemindByMail(readNoActionReportsWithAdmins, reportEventsMap, todayAtStartOfDay)
      .map { case (report, users) =>
        remindTransmittedReportByMail(report, users.map(_.email), reportEventsMap)
      }
  )

  private def extractTransmittedReportsToRemindByMail(
      readReportsWithAdmins: List[(Report, List[User])],
      reportIdEventsMap: Map[UUID, List[Event]],
      todayAtStartOfDay: LocalDateTime
  ): List[(Report, List[User])] = {

    val reportsWithNoRemindSent: List[(Report, List[User])] = readReportsWithAdmins
      // Keep only reports for which this email reminder was never sent
      .filter { case (report, _) =>
        extractEventsWithReportIdAndAction(reportIdEventsMap, report.id, EMAIL_PRO_REMIND_NO_ACTION).isEmpty
      }
      // Keep only reports with an admin
      .filter { case (_, admins) => admins.exists(_.email.nonEmpty) }
      // Keep only reports that the pro read at least 7 days ago
      .filter { case (report, _) =>
        extractEventsWithReportIdAndAction(reportIdEventsMap, report.id, REPORT_READING_BY_PRO).headOption
          .map(_.creationDate)
          .getOrElse(report.creationDate)
          .toLocalDateTime
          .isBefore(todayAtStartOfDay.minusDays(7))
      }

    val reportsWithUniqueRemindSent: List[(Report, List[User])] = readReportsWithAdmins
      // Keep only reports for which this email reminder was sent exactly once
      .filter { case (report, _) =>
        extractEventsWithReportIdAndAction(reportIdEventsMap, report.id, EMAIL_PRO_REMIND_NO_ACTION).length == 1
      }
      // Keep only reports with an admin
      .filter { case (_, admins) => admins.exists(_.email.nonEmpty) }
      // Keep only reports for which this reminder was sent at least 7 days ago
      .filter { case (report, _) =>
        extractEventsWithReportIdAndAction(
          reportIdEventsMap,
          report.id,
          EMAIL_PRO_REMIND_NO_ACTION
        ).head.creationDate.toLocalDateTime.isBefore(todayAtStartOfDay.minusDays(7))
      }

    reportsWithNoRemindSent ::: reportsWithUniqueRemindSent
  }

  private def remindTransmittedReportByMail(
      report: Report,
      adminMails: List[EmailAddress],
      reportEventsMap: Map[UUID, List[Event]]
  ) = {

    val taskExecution = for {
      _ <- eventRepository
        .create(
          Event(
            UUID.randomUUID(),
            Some(report.id),
            report.companyId,
            None,
            OffsetDateTime.now(),
            SYSTEM,
            EMAIL_PRO_REMIND_NO_ACTION,
            stringToDetailsJsValue(s"Relance envoyée à ${adminMails.mkString(", ")}")
          )
        )
      // Delay given to a pro to reply depending on how much remind he had before ( maxMaxReminderCount )
      nbRemindersLeft = MaxReminderCount - extractEventsWithReportIdAndAction(
        reportEventsMap,
        report.id,
        EMAIL_PRO_REMIND_NO_ACTION
      ).length
      reportExpirationDate = OffsetDateTime.now.plus(
        mailReminderDelay.multipliedBy(nbRemindersLeft)
      )
      _ <-
        emailService.send(
          ProReportReadReminder(
            recipients = adminMails,
            report,
            reportExpirationDate
          )
        )

    } yield ()
    toValidated(taskExecution, report.id, TaskType.RemindReadReportByMail)
  }
}
