package tasks.report

import models.report.Report
import models.report.ReportStatus
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.mvc.Results
import play.api.test.WithApplication
import repositories.event.EventFilter
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_ACTION
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_READING
import utils.AppSpec
import utils.Fixtures
import utils.TestApp

import java.time.OffsetDateTime
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class ReportClosureTaskSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val (app, components) = TestApp.buildApp()

  lazy val reportRepository = components.reportRepository
  lazy val reportClosureTask = components.reportClosureTask
  lazy val eventRepository = components.eventRepository

  val creationDate = OffsetDateTime.parse("2020-01-01T00:00:00Z")
  val taskRunDate = OffsetDateTime.parse("2020-06-01T00:00:00Z")
  val dateInThePast = taskRunDate.minusDays(5)
  val dateInTheFuture = taskRunDate.plusDays(5)

  def genReport(status: ReportStatus = ReportStatus.TraitementEnCours, expirationDate: OffsetDateTime = dateInThePast) =
    Fixtures.genDraftReport.sample.get
      .generateReport(
        maybeCompanyId = None,
        socialNetworkCompany = None,
        creationDate = creationDate,
        expirationDate = expirationDate
      )
      .copy(status = status)

  def readNewStatus(report: Report): Future[ReportStatus] =
    reportRepository.get(report.id).map(_.get.status)

  def hasEvent(report: Report, action: ActionEventValue): Future[Boolean] =
    eventRepository
      .getEvents(report.id, EventFilter(action = Some(action)))
      .map(_.nonEmpty)

  def hasZeroEvents(report: Report): Future[Boolean] =
    eventRepository
      .getEvents(report.id, EventFilter())
      .map(_.isEmpty)

  "ReportClosureTask should close the reports that need to be closed" >> {

    val reportExpired = genReport()
    val reportExpiredSeenByPro = genReport(status = ReportStatus.Transmis)
    val reportExpiredButAlreadyClosed = genReport(status = ReportStatus.NonConsulte)
    val reportNotExpired = genReport(expirationDate = dateInTheFuture)

    def setup(): Future[Unit] =
      for {
        _ <- reportRepository.create(reportExpired)
        _ <- reportRepository.create(reportExpiredSeenByPro)
        _ <- reportRepository.create(reportExpiredButAlreadyClosed)
        _ <- reportRepository.create(reportNotExpired)
      } yield ()

    def check(): Future[Unit] =
      for {
        _ <- readNewStatus(reportExpired).map(_ must be(ReportStatus.NonConsulte))
        _ <- hasEvent(reportExpired, REPORT_CLOSED_BY_NO_READING) map (_ must beTrue)

        _ <- readNewStatus(reportExpiredSeenByPro).map(_ must be(ReportStatus.ConsulteIgnore))
        _ <- hasEvent(reportExpiredSeenByPro, REPORT_CLOSED_BY_NO_ACTION) map (_ must beTrue)

        _ <- readNewStatus(reportExpiredButAlreadyClosed).map(_ must be(reportExpiredButAlreadyClosed.status))
        _ <- hasZeroEvents(reportExpiredButAlreadyClosed) map (_ must beTrue)

        _ <- readNewStatus(reportNotExpired).map(_ must be(reportNotExpired.status))
        _ <- hasZeroEvents(reportExpiredButAlreadyClosed) map (_ must beTrue)
      } yield ()

    new WithApplication(app) {
      Await.result(
        for {
          _ <- setup()
          _ <- reportClosureTask.runTask(taskRunDate)
          _ <- check()
        } yield (),
        Duration.Inf
      )
    }
  }
}
