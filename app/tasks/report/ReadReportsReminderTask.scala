package tasks.report

import config.TaskConfiguration
import models.Event.stringToDetailsJsValue
import models.Event
import models.Report
import models.User
import play.api.Logger
import repositories.EventRepository
import services.Email.ProReportReadReminder
import services.MailService
import tasks.model.TaskType
import tasks.report.ReportTask.MaxReminderCount
import tasks.report.ReportTask.extractEventsWithAction
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
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReadReportsReminderTask @Inject() (
    taskConfiguration: TaskConfiguration,
    eventRepository: EventRepository,
    emailService: MailService
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  val mailReminderDelay: Period = taskConfiguration.report.mailReminderDelay

  def sendReminder(
      transmittedReportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      startingPoint: LocalDateTime
  ): Future[List[TaskExecutionResult]] = Future.sequence(
    extractTransmittedReportsToRemindByMail(transmittedReportsWithAdmins, reportEventsMap, startingPoint)
      .map { case (report, users) =>
        remindTransmittedReportByMail(report, users.map(_.email), reportEventsMap)
      }
  )

  private def extractTransmittedReportsToRemindByMail(
      readReportsWithAdmins: List[(Report, List[User])],
      reportIdEventsMap: Map[UUID, List[Event]],
      startingDate: LocalDateTime
  ): List[(Report, List[User])] = {

    val reportsWithNoRemindSent: List[(Report, List[User])] = readReportsWithAdmins
      .filter { case (report, _) =>
        // Filter reports with no "NO_ACTION" reminder events
        extractEventsWithAction(report.id, reportIdEventsMap, EMAIL_PRO_REMIND_NO_ACTION).isEmpty
      }
      .filter { case (_, users) =>
        // Filter reports with activated accounts
        users.exists(_.email.nonEmpty)
      }
      .filter { case (report, _) =>
        // Filter reports read by pro before 7 days ago
        extractEventsWithAction(report.id, reportIdEventsMap, REPORT_READING_BY_PRO).headOption
          .flatMap(_.creationDate)
          .getOrElse(report.creationDate)
          .toLocalDateTime
          .isBefore(startingDate.minusDays(7))
      }

    val reportsWithUniqueRemindSent: List[(Report, List[User])] = readReportsWithAdmins
      .filter { case (report, _) =>
        extractEventsWithAction(report.id, reportIdEventsMap, EMAIL_PRO_REMIND_NO_ACTION).length == 1
      }
      .filter { case (_, users) =>
        // Filter reports with activated accounts
        users.exists(_.email.nonEmpty)
      }
      .filter { case (report, _) =>
        // Filter reports with one EMAIL_PRO_REMIND_NO_ACTION remind before 7 days ago
        extractEventsWithAction(
          report.id,
          reportIdEventsMap,
          EMAIL_PRO_REMIND_NO_ACTION
        ).head.creationDate
          .exists(_.toLocalDateTime.isBefore(startingDate.minusDays(7)))
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
      // Delay given to a pro to reply depending on how much remind he had before ( maxMaxReminderCount )
      reportExpirationDate = OffsetDateTime.now.plus(
        mailReminderDelay.multipliedBy(
          MaxReminderCount - extractEventsWithAction(report.id, reportEventsMap, EMAIL_PRO_REMIND_NO_ACTION).length
        )
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
