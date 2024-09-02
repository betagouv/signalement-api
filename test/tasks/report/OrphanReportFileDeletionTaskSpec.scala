package tasks.report

import cats.implicits.catsSyntaxOptionId
import models.report.ReportFileOrigin.Consumer
import models.report.ReportStatus.TraitementEnCours
import models.report.Report
import models.report.ReportFile
import models.report.reportfile.ReportFileId
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.mvc.Results
import play.api.test.WithApplication
import repositories.event.EventFilter
import utils.Constants.ActionEvent.ActionEventValue
import utils.AppSpec
import utils.Fixtures
import utils.TestApp

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class OrphanReportFileDeletionTaskSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val (app, components) = TestApp.buildApp()

  lazy val reportRepository     = components.reportRepository
  lazy val reportFileRepository = components.reportFileRepository
  lazy val fileDeletionTask     = components.orphanReportFileDeletionTask
  lazy val eventRepository      = components.eventRepository

  val creationDate    = OffsetDateTime.parse("2020-01-01T00:00:00Z")
  val taskRunDate     = OffsetDateTime.parse("2020-06-01T00:00:00Z")
  val dateInThePast   = taskRunDate.minusDays(5)
  val dateInTheFuture = taskRunDate.plusDays(5)

  def genReport() =
    Fixtures.genDraftReport.sample.get
      .generateReport(
        maybeCompanyId = None,
        maybeCompany = None,
        creationDate = creationDate,
        expirationDate = dateInTheFuture
      )
      .copy(status = TraitementEnCours)

  def genReportFile(reportId: Option[UUID], creationDate: OffsetDateTime) =
    ReportFile(
      ReportFileId(UUID.randomUUID()),
      reportId = reportId,
      creationDate,
      filename = UUID.randomUUID().toString,
      storageFilename = UUID.randomUUID().toString,
      origin = Consumer,
      avOutput = None
    )

  def hasEvent(report: Report, action: ActionEventValue): Future[Boolean] =
    eventRepository
      .getEvents(report.id, EventFilter(action = Some(action)))
      .map(_.nonEmpty)

  "OrphanReportFileDeletionTask should remove orphan reports" >> {

    val report                         = genReport()
    val recentReportFileLinkedToReport = genReportFile(reportId = report.id.some, creationDate = OffsetDateTime.now())
    val oldReportFileLinkedToReport =
      genReportFile(reportId = report.id.some, creationDate = OffsetDateTime.now().minusYears(3))
    val recentReportFile = genReportFile(reportId = None, creationDate = OffsetDateTime.now())
    val oldReportFile    = genReportFile(reportId = None, creationDate = OffsetDateTime.now().minusYears(3))

    def setup(): Future[Unit] =
      for {
        _ <- reportRepository.create(report)
        _ <- reportFileRepository.create(recentReportFileLinkedToReport)
        _ <- reportFileRepository.create(oldReportFileLinkedToReport)
        _ <- reportFileRepository.create(recentReportFile)
        _ <- reportFileRepository.create(oldReportFile)
        _ <- reportRepository.get(report.id) map (_.isDefined must beTrue)
        _ <- reportFileRepository.get(recentReportFileLinkedToReport.id) map (_.isDefined must beTrue)
        _ <- reportFileRepository.get(oldReportFileLinkedToReport.id) map (_.isDefined must beTrue)
        _ <- reportFileRepository.get(recentReportFile.id) map (_.isDefined must beTrue)
        _ <- reportFileRepository.get(oldReportFile.id) map (_.isDefined must beTrue)
      } yield ()

    def check(): Future[Unit] =
      for {
        _ <- reportRepository.get(report.id) map (_.isDefined must beTrue)
        _ <- reportFileRepository.get(recentReportFileLinkedToReport.id) map (_.isDefined must beTrue)
        _ <- reportFileRepository.get(oldReportFileLinkedToReport.id) map (_.isDefined must beTrue)
        _ <- reportFileRepository.get(recentReportFile.id) map (_.isDefined must beTrue)
        _ <- reportFileRepository.get(oldReportFile.id) map (_.isDefined must beFalse)
      } yield ()

    new WithApplication(app) {
      Await.result(
        for {
          _ <- setup()
          _ <- fileDeletionTask.runTask()
          _ <- check()
        } yield (),
        Duration.Inf
      )
    }
  }
}
