package tasks.report

import akka.actor.ActorSystem
import cats.implicits._
import config.TaskConfiguration
import models.event.Event
import models.event.Event.stringToDetailsJsValue
import models.report.Report
import models.report.ReportStatus
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import services.Email.ConsumerReportClosedNoAction
import services.Email.ConsumerReportClosedNoReading
import services.MailService
import tasks.getTodayAtStartOfDayParis
import tasks.scheduleTask
import utils.Constants.ActionEvent.EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.ActionEvent.EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_READING
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_READING
import utils.Constants.EventType.CONSO
import utils.Constants.EventType.SYSTEM

import java.time._
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

class ReportClosureTask(
    actorSystem: ActorSystem,
    reportRepository: ReportRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    companyRepository: CompanyRepositoryInterface,
    mailService: MailService,
    taskConfiguration: TaskConfiguration
)(implicit val executionContext: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  scheduleTask(
    actorSystem,
    taskConfiguration,
    startTime = taskConfiguration.reportClosure.startTime,
    interval = 1.day
  )(runTask())

  def runTask(): Unit = {
    val todayAtStartOfDay = getTodayAtStartOfDayParis()
    logger.info(s"Traitement de fermeture des signalement expirés (using time ${todayAtStartOfDay})")
    val ongoingReportsStatus = List(ReportStatus.TraitementEnCours, ReportStatus.Transmis)
    for {
      reportsToClose <- reportRepository.getByStatusAndExpired(ongoingReportsStatus, now = todayAtStartOfDay)
      _ <- closeExpiredReportsWithErrorHandling(reportsToClose)
    } yield ()
    ()
  }

  private def closeExpiredReportsWithErrorHandling(reports: List[Report]): Future[Unit] = {
    logger.info(s"Closing ${reports.length} reports")
    for {
      successesOrFailuresList <- Future.sequence(reports.map { report =>
        closeExpiredReport(report).transform {
          case Success(_) => Success(Right(report.id))
          case Failure(err) =>
            logger.error(s"Error closing report ${report.id}", err)
            Success(Left(report.id))
        }
      })
      (failures, successes) = successesOrFailuresList.partitionMap(identity)
      _ = logger.info(s"Successful closures for ${successes.length} reports")
      _ = if (failures.nonEmpty) logger.error(s"Failed to close ${failures.length} reports")
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
        ConsumerReportClosedNoAction,
        EMAIL_CONSUMER_REPORT_CLOSED_BY_NO_ACTION
      )
    } else {
      (
        ReportStatus.NonConsulte,
        REPORT_CLOSED_BY_NO_READING,
        "Clôture automatique : signalement non consulté",
        ConsumerReportClosedNoReading,
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
      _ <- mailService.send(email(report, maybeCompany))
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

}
