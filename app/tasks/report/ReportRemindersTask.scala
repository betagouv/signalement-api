package tasks.report

import org.apache.pekko.actor.ActorSystem
import config.TaskConfiguration
import models._
import models.event.Event
import models.event.Event.stringToDetailsJsValue
import models.report.Report
import models.report.ReportStatus
import orchestrators.CompaniesVisibilityOrchestrator
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import services.emails.EmailDefinitionsPro.ProReportsLastChanceReminder
import services.emails.EmailDefinitionsPro.ProReportsReadReminder
import services.emails.EmailDefinitionsPro.ProReportsUnreadReminder
import services.emails.BaseEmail
import services.emails.MailServiceInterface
import tasks.ScheduledTask
import tasks.getTodayAtStartOfDayParis
import tasks.model.TaskSettings.DailyTaskSettings
import utils.Constants.ActionEvent._
import utils.Constants.EventType.SYSTEM
import utils.EmailAddress
import utils.Logs.RichLogger

import java.time._
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
class ReportRemindersTask(
    actorSystem: ActorSystem,
    reportRepository: ReportRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    mailService: MailServiceInterface,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface
)(implicit val executionContext: ExecutionContext)
    extends ScheduledTask(4, "report_reminders_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = DailyTaskSettings(
    startTime = taskConfiguration.reportReminders.startTime
  )

  // In practice, since we require 7 full days between the previous email and the next one,
  // the email will fire at J+8
  // Typically (if the pro account existed when the report was created):
  // At J+0, the pro receives the "new report" email
  // At J+8 during the night, the pro receives a reminder email
  // At J+16 during the night, the pro receives the second reminder email
  // At J+24 last reminder
  // At J+25 the report is closed
  val delayBetweenReminderEmails: Period = Period.ofDays(7)
  val maxReminderCount                   = 2

  override def runTask(): Future[Unit] =
    runTask(taskRunDate = getTodayAtStartOfDayParis()).map { case (failures, successes) =>
      logger.info(
        s"Successfully sent ${successes.length} reminder emails sent for ${successes.map(_.length).sum} reports"
      )
      if (failures.nonEmpty)
        logger.error(s"Failed to send ${failures.length} reminder emails for ${failures.map(_.length).sum} reports")
    }

  def runTask(taskRunDate: OffsetDateTime): Future[(List[List[UUID]], List[List[UUID]])] = {
    val ongoingReportsStatus = ReportStatus.statusOngoing
    for {
      ongoingReportsWithUsers <- getReportsByStatusWithUsers(ongoingReportsStatus)
      ongoingReportsWithAtLeastOneUser = ongoingReportsWithUsers.filter(_._2.nonEmpty)
      _ = logger.info(s"Found ${ongoingReportsWithAtLeastOneUser.size} potential reports")
      eventsByReportId <- eventRepository.fetchEventsOfReports(ongoingReportsWithAtLeastOneUser.map(_._1))
      finalReportsWithUsers = ongoingReportsWithAtLeastOneUser.filter { case (report, _) =>
        shouldSendReminderEmail(report, taskRunDate, eventsByReportId)
      }
      _ = logger.info(s"Found ${finalReportsWithUsers.size} reports for which we should send a reminder")
      result <- sendReminderEmailsWithErrorHandling(taskRunDate, finalReportsWithUsers)
    } yield result
  }

  private def sendReminderEmailsWithErrorHandling(
      taskRunDate: OffsetDateTime,
      reportsWithUsers: List[(Report, List[User])]
  ): Future[(List[List[UUID]], List[List[UUID]])] = {
    logger.info(s"Sending reminders for ${reportsWithUsers.length} reports")
    val reportsPerUsers           = reportsWithUsers.groupBy(_._2).view.mapValues(_.map(_._1))
    val reportsPerCompanyPerUsers = reportsPerUsers.mapValues(_.groupBy(_.companyId)).mapValues(_.values)

    for {
      successesOrFailuresList <- Future.sequence(reportsPerCompanyPerUsers.toList.flatMap {
        case (users, reportsPerCompany) =>
          reportsPerCompany
            .map {
              reports =>
                val (reportsClosingTomorrow, otherReports) = reports.partition(isLastDay(_, taskRunDate))
                val (readByPros, notReadByPros)            = otherReports.partition(_.isReadByPro)

                for {
                  reportsClosingTomorrowSent <- sendReminderEmailIfAtLeastOneReport(
                    reportsClosingTomorrow,
                    users,
                    ProReportsLastChanceReminder.Email,
                    EMAIL_LAST_CHANCE_REMINDER_ACTION
                  )
                  readByProsSent <- sendReminderEmailIfAtLeastOneReport(
                    readByPros,
                    users,
                    ProReportsReadReminder.Email,
                    EMAIL_PRO_REMIND_NO_ACTION
                  )
                  notReadByProsSent <- sendReminderEmailIfAtLeastOneReport(
                    notReadByPros,
                    users,
                    ProReportsUnreadReminder.Email,
                    EMAIL_PRO_REMIND_NO_READING
                  )
                } yield List(readByProsSent, notReadByProsSent, reportsClosingTomorrowSent).flatten
            }
      })
      (failures, successes) = successesOrFailuresList.flatten.partitionMap(identity)
    } yield (failures, successes)
  }

  private def sendReminderEmailIfAtLeastOneReport(
      reports: List[Report],
      users: List[User],
      email: (List[EmailAddress], List[Report], Period) => BaseEmail,
      action: ActionEventValue
  ): Future[Option[Either[List[UUID], List[UUID]]]] =
    if (reports.nonEmpty) {
      sendReminderEmail(reports, users, email, action).transform {
        case Success(_) => Success(Some(Right(reports.map(_.id))))
        case Failure(err) =>
          logger.errorWithTitle(
            "report_reminders_task_item_error",
            s"Error sending reminder email for reports ${reports.map(_.id)} to ${users.length} users",
            err
          )
          Success(Some(Left(reports.map(_.id))))
      }
    } else {
      Future.successful(None)
    }

  private def shouldSendReminderEmail(
      report: Report,
      taskRunDate: OffsetDateTime,
      eventsByReportId: Map[UUID, List[Event]]
  ): Boolean = {
    val reminderEmailsActions = List(EMAIL_PRO_REMIND_NO_READING, EMAIL_PRO_REMIND_NO_ACTION)
    val allEmailsToProActions = reminderEmailsActions :+ EMAIL_PRO_NEW_REPORT
    val previousEmailsEvents =
      eventsByReportId
        .getOrElse(report.id, Nil)
        .filter(e => allEmailsToProActions.contains(e.action))
    val hadMaxReminderEmails =
      previousEmailsEvents.count(e => reminderEmailsActions.contains(e.action)) >= maxReminderCount
    val hadARecentEmail =
      previousEmailsEvents.exists(_.creationDate.isAfter(taskRunDate.minus(delayBetweenReminderEmails)))

    val shouldSendEmail = (!hadMaxReminderEmails && !hadARecentEmail) || isLastDay(report, taskRunDate)
    shouldSendEmail
  }

  private def isLastDay(report: Report, taskRunDate: OffsetDateTime) =
    taskRunDate.toLocalDate.plusDays(1).isEqual(report.expirationDate.toLocalDate)

  private def sendReminderEmail(
      reports: List[Report],
      users: List[User],
      email: (List[EmailAddress], List[Report], Period) => BaseEmail,
      action: ActionEventValue
  ): Future[Unit] = {
    val emailAddresses = users.map(_.email)

    logger.infoWithTitle("report_reminders_task_item", s"Sending reports ${reports.map(_.id)}")
    for {
      _ <- mailService.send(email(emailAddresses, reports, delayBetweenReminderEmails))
      _ <- Future.sequence(
        reports.map { report =>
          eventRepository.create(
            Event(
              UUID.randomUUID(),
              Some(report.id),
              report.companyId,
              None,
              OffsetDateTime.now(),
              SYSTEM,
              action,
              stringToDetailsJsValue(s"Relance envoyée à ${emailAddresses.mkString(", ")}")
            )
          )
        }
      )
    } yield ()
  }

  private[this] def getReportsByStatusWithUsers(status: List[ReportStatus]): Future[List[(Report, List[User])]] =
    for {
      reports <- reportRepository.getByStatus(status)
      companiesSiretsAndIds = reports.flatMap(r =>
        for {
          siret <- r.companySiret
          id    <- r.companyId
        } yield (siret, id)
      )
      usersByCompanyId <- companiesVisibilityOrchestrator.fetchUsersWithHeadOffices(companiesSiretsAndIds.distinct)
    } yield reports.flatMap(r => r.companyId.map(companyId => (r, usersByCompanyId.getOrElse(companyId, Nil))))
}
