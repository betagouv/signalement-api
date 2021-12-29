package tasks

import config.AppConfigLoader
import models.Event.stringToDetailsJsValue
import models.Event
import models.Report
import models.User
import play.api.Logger
import repositories.EventRepository
import services.Email.ProReportUnreadReminder
import services.MailService
import tasks.ReportTask.extractEventsWithAction
import tasks.model.TaskOutcome.FailedTask
import tasks.model.TaskOutcome.SuccessfulTask
import tasks.model.TaskOutcome
import tasks.model.TaskType
import utils.Constants.ActionEvent.EMAIL_PRO_NEW_REPORT
import utils.Constants.ActionEvent.EMAIL_PRO_REMIND_NO_READING
import utils.Constants.EventType.SYSTEM
import utils.EmailAddress

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class UnreadReportsReminderTask @Inject() (
    appConfigLoader: AppConfigLoader,
    eventRepository: EventRepository,
    emailService: MailService
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  val noAccessReadingDelay = appConfigLoader.get.report.noAccessReadingDelay
  val mailReminderDelay = appConfigLoader.get.report.mailReminderDelay

  def sendReminder(
      onGoingReportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      startingPoint: LocalDateTime
  ): Future[List[TaskOutcome]] = Future.sequence(
    extractUnreadReportsToRemindByMail(onGoingReportsWithAdmins, reportEventsMap, startingPoint)
      .map { case (report, users) =>
        remindUnreadReportByMail(report, users.map(_.email), reportEventsMap)
      }
  )

  private def extractUnreadReportsToRemindByMail(
      reportsWithAdmins: List[(Report, List[User])],
      reportEventsMap: Map[UUID, List[Event]],
      now: LocalDateTime
  ): List[(Report, List[User])] = {

    val reportWithNoRemind: List[(Report, List[User])] = reportsWithAdmins
      .filter(reportWithAdmins =>
        extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_REMIND_NO_READING).isEmpty
      )
      .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email.nonEmpty))
      .filter(reportWithAdmins =>
        extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_NEW_REPORT).headOption
          .flatMap(_.creationDate)
          .getOrElse(reportWithAdmins._1.creationDate)
          .toLocalDateTime
          .isBefore(now.minusDays(7))
      )

    val reportWithUniqueRemind: List[(Report, List[User])] = reportsWithAdmins
      .filter(reportWithAdmins =>
        extractEventsWithAction(reportWithAdmins._1.id, reportEventsMap, EMAIL_PRO_REMIND_NO_READING).length == 1
      )
      .filter(reportWithAdmins => reportWithAdmins._2.exists(_.email.nonEmpty))
      .filter(reportWithAdmins =>
        extractEventsWithAction(
          reportWithAdmins._1.id,
          reportEventsMap,
          EMAIL_PRO_REMIND_NO_READING
        ).head.creationDate.exists(_.toLocalDateTime.isBefore(now.minusDays(7)))
      )

    reportWithNoRemind ::: reportWithUniqueRemind
  }

  private def remindUnreadReportByMail(
      report: Report,
      adminMails: List[EmailAddress],
      reportEventsMap: Map[UUID, List[Event]]
  ): Future[TaskOutcome] = {
    val remind: Future[SuccessfulTask] = for {
      _ <- eventRepository.createEvent(
        Event(
          Some(UUID.randomUUID()),
          Some(report.id),
          report.companyId,
          None,
          Some(OffsetDateTime.now()),
          SYSTEM,
          EMAIL_PRO_REMIND_NO_READING,
          stringToDetailsJsValue(s"Relance envoyée à ${adminMails.mkString(", ")}")
        )
      )
      reportExpirationDate = ReportTask.computeReportExpirationDate(
        mailReminderDelay,
        report.id,
        reportEventsMap,
        EMAIL_PRO_REMIND_NO_READING
      )
      _ = logger.debug(s"Sending email")
      _ <- emailService.send(ProReportUnreadReminder(adminMails, report, reportExpirationDate))

    } yield SuccessfulTask(report.id, TaskType.RemindReportByMail)

    remind.recoverWith { case err =>
      logger.error("Error processing reminder task", err)
      Future.successful(FailedTask(report.id, TaskType.RemindReportByMail, err))
    }

  }

}
