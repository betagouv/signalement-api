package tasks.report

import akka.actor.ActorSystem
import config.TaskConfiguration
import models._
import models.event.Event
import models.event.Event.stringToDetailsJsValue
import models.report.Report
import models.report.ReportStatus
import orchestrators.CompaniesVisibilityOrchestrator
import play.api.Logger
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import services.Email.ProReportsReadReminder
import services.Email.ProReportsUnreadReminder
import services.MailService
import tasks.getTodayAtStartOfDayParis
import tasks.scheduleTask
import utils.Constants.ActionEvent._
import utils.Constants.EventType.SYSTEM
import java.time._
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import utils.Logs.RichLogger
class ReportRemindersTask(
    actorSystem: ActorSystem,
    reportRepository: ReportRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    mailService: MailService,
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    taskConfiguration: TaskConfiguration
)(implicit val executionContext: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  val conf = taskConfiguration.reportReminders

  // In practice, since we require 7 full days between the previous email and the next one,
  // the email will fire at J+8
  // Typically (if the pro account existed when the report was created):
  // At J+0, the pro receives the "new report" email
  // At J+8 during the night, the pro receives a reminder email
  // At J+16 during the night, the pro receives the second reminder email
  // At J+25 the report is closed
  val delayBetweenReminderEmails: Period = Period.ofDays(7)
  val maxReminderCount = 2

  scheduleTask(
    actorSystem,
    taskConfiguration,
    startTime = conf.startTime,
    interval = conf.intervalInHours,
    taskName = "report_reminders_task"
  )(runTask(taskRunDate = getTodayAtStartOfDayParis()))

  def runTask(taskRunDate: OffsetDateTime): Future[Unit] = {
    val ongoingReportsStatus = List(ReportStatus.TraitementEnCours, ReportStatus.Transmis)
    for {
      ongoingReportsWithUsers <- getReportsByStatusWithUsers(ongoingReportsStatus)
      ongoingReportsWithAtLeastOneUser = ongoingReportsWithUsers.filter(_._2.nonEmpty)
      _ = logger.info(s"Found ${ongoingReportsWithAtLeastOneUser.size} potential reports")
      eventsByReportId <- eventRepository.fetchEventsOfReports(ongoingReportsWithAtLeastOneUser.map(_._1))
      finalReportsWithUsers = ongoingReportsWithAtLeastOneUser.filter { case (report, _) =>
        shouldSendReminderEmail(report, taskRunDate, eventsByReportId)
      }
      _ = logger.info(s"Found ${finalReportsWithUsers.size} reports for which we should send a reminder")
      _ <- sendReminderEmailsWithErrorHandling(finalReportsWithUsers)
    } yield ()
  }

  private def sendReminderEmailsWithErrorHandling(reportsWithUsers: List[(Report, List[User])]): Future[Unit] = {
    logger.info(s"Sending reminders for ${reportsWithUsers.length} reports")
    val reportsPerUsers = reportsWithUsers.groupBy(_._2).view.mapValues(_.map(_._1))
    val reportsPerCompanyPerUsers = reportsPerUsers.mapValues(_.groupBy(_.companyId)).mapValues(_.values).toMap

    for {
      successesOrFailuresList <- Future.sequence(reportsPerCompanyPerUsers.toList.flatMap {
        case (users, reportsPerCompany) =>
          reportsPerCompany.map { reports =>
            logger.infoWithTitle("report_reminders_task_item", s"Closed reports ${reports.map(_.id)}")
            sendReminderEmail(reports, users).transform {
              case Success(_) => Success(Right(reports.map(_.id)))
              case Failure(err) =>
                logger.errorWithTitle(
                  "report_reminders_task_item_error",
                  s"Error sending reminder email for reports ${reports.map(_.id)} to ${users.length} users",
                  err
                )
                Success(Left(reports.map(_.id)))
            }
          }

      })
      (failures, successes) = successesOrFailuresList.partitionMap(identity)
      _ = logger.info(s"Successful reminder emails sent for ${successes.length} reports")
      _ = if (failures.nonEmpty) logger.error(s"Failed to send reminder emails for ${failures.length} reports")
    } yield ()
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
    val shouldSendEmail = !hadMaxReminderEmails && !hadARecentEmail
    shouldSendEmail
  }

  private def sendReminderEmail(
      reports: List[Report],
      users: List[User]
  ): Future[Unit] = {
    val emailAddresses = users.map(_.email)
    val (readByPros, notReadByPros) = reports.partition(_.isReadByPro)

    logger.debug(s"Sending reminder email")
    for {
      _ <- mailService.send(ProReportsReadReminder(emailAddresses, readByPros, delayBetweenReminderEmails))
      _ <- mailService.send(ProReportsUnreadReminder(emailAddresses, notReadByPros, delayBetweenReminderEmails))
      _ <- Future.sequence(
        readByPros.map { report =>
          eventRepository.create(
            Event(
              UUID.randomUUID(),
              Some(report.id),
              report.companyId,
              None,
              OffsetDateTime.now(),
              SYSTEM,
              EMAIL_PRO_REMIND_NO_ACTION,
              stringToDetailsJsValue(s"Relance envoyée à ${emailAddresses.mkString(", ")}")
            )
          )
        }
      )
      _ <- Future.sequence(
        notReadByPros.map { report =>
          eventRepository.create(
            Event(
              UUID.randomUUID(),
              Some(report.id),
              report.companyId,
              None,
              OffsetDateTime.now(),
              SYSTEM,
              EMAIL_PRO_REMIND_NO_READING,
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
          id <- r.companyId
        } yield (siret, id)
      )
      usersByCompanyId <- companiesVisibilityOrchestrator.fetchUsersWithHeadOffices(companiesSiretsAndIds)
    } yield reports.flatMap(r => r.companyId.map(companyId => (r, usersByCompanyId.getOrElse(companyId, Nil))))

}
