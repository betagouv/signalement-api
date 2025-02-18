package tasks.report

import org.apache.pekko.actor.ActorSystem
import cats.implicits._
import config.TaskConfiguration
import models.event.Event
import models.event.Event.stringToDetailsJsValue
import models.report.Report
import models.report.ReportStatus
import play.api.i18n.MessagesApi
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.tasklock.TaskRepositoryInterface
import services.emails.EmailDefinitionsConsumer.ConsumerReportClosedNoAction
import services.emails.EmailDefinitionsConsumer.ConsumerReportClosedNoReading
import services.emails.BaseEmail
import services.emails.MailService
import tasks.ScheduledTask
import tasks.getTodayAtStartOfDayParis
import tasks.model.TaskSettings.DailyTaskSettings
import utils.Constants.ActionEvent.EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.ActionEvent.EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_READING
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_READING
import utils.Constants.EventType.CONSO
import utils.Constants.EventType.SYSTEM
import utils.Logs.RichLogger

import java.time._
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

class ReportClosureTask(
    actorSystem: ActorSystem,
    reportRepository: ReportRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    companyRepository: CompanyRepositoryInterface,
    mailService: MailService,
    taskConfiguration: TaskConfiguration,
    taskRepository: TaskRepositoryInterface,
    messagesApi: MessagesApi
)(implicit val executionContext: ExecutionContext)
    extends ScheduledTask(2, "report_closure_task", taskRepository, actorSystem, taskConfiguration) {

  override val taskSettings = DailyTaskSettings(startTime = taskConfiguration.reportClosure.startTime)

  override def runTask(): Future[Unit] = runTask(taskRunDate = getTodayAtStartOfDayParis())

  def runTask(taskRunDate: OffsetDateTime): Future[Unit] = {
    val ongoingReportsStatus = List(ReportStatus.TraitementEnCours, ReportStatus.Transmis)
    for {
      reportsToClose <- reportRepository.getByStatusAndExpired(ongoingReportsStatus, now = taskRunDate)
      _              <- closeExpiredReportsWithErrorHandling(reportsToClose)
    } yield ()
  }

  private def closeExpiredReportsWithErrorHandling(reports: List[Report]): Future[Unit] = {
    logger.info(s"Closing ${reports.length} reports")
    for {
      successesOrFailuresList <- Future.sequence(reports.map { report =>
        logger.infoWithTitle("report_closure_task_item", s"Closing report ${report.id}")
        closeExpiredReport(report).transform {
          case Success(_) =>
            Success(Right(report.id))
          case Failure(err) =>
            logger.errorWithTitle("report_closure_task_item_error", s"Error closing report ${report.id}", err)
            Success(Left(report.id))
        }
      })
      (failures, successes) = successesOrFailuresList.partitionMap(identity)
      _                     = logger.info(s"Successful closures for ${successes.length} reports")
      _                     = if (failures.nonEmpty) logger.error(s"Failed to close ${failures.length} reports")
    } yield ()
  }

  private def closeExpiredReport(report: Report): Future[Unit] = {
    // Historically the closure for read or unread reports was handled separately so we had different events, status, etc.
    // Now this distinction makes less sense. We kept the same system of events/status nonetheless.
    val (newStatus, closureEventAction, closureEventDetails, email, emailEventAction) = if (report.isReadByPro) {
      (
        ReportStatus.ConsulteIgnore,
        REPORT_CLOSED_BY_NO_ACTION,
        "Clôture automatique : signalement consulté ignoré",
        ConsumerReportClosedNoAction.Email,
        EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_ACTION
      )
    } else {
      (
        ReportStatus.NonConsulte,
        REPORT_CLOSED_BY_NO_READING,
        "Clôture automatique : signalement non consulté",
        ConsumerReportClosedNoReading.Email,
        EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_READING
      )
    }
    for {
      _ <- reportRepository.update(report.id, report.copy(status = newStatus))
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          None,
          OffsetDateTime.now(),
          SYSTEM,
          closureEventAction,
          stringToDetailsJsValue(closureEventDetails)
        )
      )
      maybeCompany <- report.companySiret.map(companyRepository.findBySiret).flatSequence
      consumerEmail = email(report, maybeCompany, messagesApi)
      _ <- eventuallySendConsumerEmail(report, consumerEmail)
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          Some(report.id),
          report.companyId,
          None,
          OffsetDateTime.now(),
          CONSO,
          emailEventAction
        )
      )
    } yield ()
  }

  private def eventuallySendConsumerEmail(report: Report, email: BaseEmail) = {
    val hasReportBeenReOpened = report.reopenDate.isDefined
    // We don't want the consumer to be notified when a pro is requesting a report reopening.
    // The consumer will only be notified when the pro will reply.
    if (!hasReportBeenReOpened) {
      mailService.send(email)
    } else {
      logger.debug("Report has been re-opened, it is not necessary to inform the consumer")
      Future.unit
    }

  }
}
