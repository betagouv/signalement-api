package tasks.report

import cats.implicits.toTraverseOps
import models.company.CompanyWithAccess
import models.report.Report
import models.report.ReportStatus
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.mvc.Results
import play.api.test.WithApplication
import utils.AppSpec
import utils.Fixtures
import utils.TestApp

import java.time.OffsetDateTime
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class SampleDataGenerationTaskTest(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val (app, components) = TestApp.buildApp()

  lazy val reportRepository         = components.reportRepository
  lazy val sampleDataGenerationTask = components.sampleDataGenerationTask
  lazy val sampleDataService        = components.sampleDataService
  lazy val userRepository           = components.userRepository
  lazy val companyAccessRepository  = components.companyAccessRepository

  val taskRunDate     = OffsetDateTime.parse("2020-06-01T00:00:00Z")
  val dateInThePast   = taskRunDate.minusDays(5)
  val dateInTheFuture = taskRunDate.plusDays(5)

  def genReport(status: ReportStatus = ReportStatus.TraitementEnCours, expirationDate: OffsetDateTime = dateInThePast) =
    Fixtures.genDraftReport.sample.get
      .generateReport(
        maybeCompanyId = None,
        maybeCompany = None,
        creationDate = OffsetDateTime.now(),
        expirationDate = expirationDate
      )
      .copy(status = status)

  def readReport(report: Report): Future[Option[Report]] =
    reportRepository.get(report.id)

  "SampleDataGenerationTaskTest should create sample data but leave other data untouched" >> {

    val reportExpired                 = genReport()
    val reportExpiredSeenByPro        = genReport(status = ReportStatus.Transmis)
    val reportExpiredButAlreadyClosed = genReport(status = ReportStatus.NonConsulte)
    val reportNotExpired              = genReport(expirationDate = dateInTheFuture)

    def setup(): Future[Unit] =
      for {
        _ <- reportRepository.create(reportExpired)
        _ <- reportRepository.create(reportExpiredSeenByPro)
        _ <- reportRepository.create(reportExpiredButAlreadyClosed)
        _ <- reportRepository.create(reportNotExpired)
      } yield ()

    def validateCompanyAndReport(companyWithAccess: List[CompanyWithAccess]) = for {
      reportList <- companyWithAccess.map(_.company.id).flatTraverse(c => reportRepository.getReports(c))
    } yield (reportList.nonEmpty && companyWithAccess.nonEmpty) must beTrue

    def check(): Future[Unit] =
      for {
        // Old reports should still exists
        x <- readReport(reportExpired)
        _ = println(s"------------------ x = ${reportExpired.id} ------------------")
        _ <- readReport(reportExpired).map(_.isDefined must beTrue)
        _ <- readReport(reportExpiredSeenByPro).map(_.isDefined must beTrue)
        _ <- readReport(reportExpiredButAlreadyClosed).map(_.isDefined must beTrue)
        _ <- readReport(reportNotExpired).map(_.isDefined must beTrue)

        _ <- userRepository.get(sampleDataService.proUserA.id).map(_.isDefined must beTrue)
        _ <- userRepository.get(sampleDataService.proUserB.id).map(_.isDefined must beTrue)
        _ <- userRepository.get(sampleDataService.proUserC.id).map(_.isDefined must beTrue)
        _ <- userRepository.get(sampleDataService.proUserD.id).map(_.isDefined must beTrue)
        _ <- userRepository.get(sampleDataService.proUserE.id).map(_.isDefined must beTrue)
        _ <- userRepository.get(sampleDataService.proUserF.id).map(_.isDefined must beTrue)

        _ <- companyAccessRepository
          .fetchCompaniesWithLevel(sampleDataService.proUserA)
          .map(validateCompanyAndReport)
        _ <- companyAccessRepository
          .fetchCompaniesWithLevel(sampleDataService.proUserB)
          .map(validateCompanyAndReport)
        _ <- companyAccessRepository
          .fetchCompaniesWithLevel(sampleDataService.proUserC)
          .map(validateCompanyAndReport)
        _ <- companyAccessRepository
          .fetchCompaniesWithLevel(sampleDataService.proUserD)
          .map(_.nonEmpty must beTrue)
        _ <- companyAccessRepository
          .fetchCompaniesWithLevel(sampleDataService.proUserE)
          .map(validateCompanyAndReport)
        _ <- companyAccessRepository
          .fetchCompaniesWithLevel(sampleDataService.proUserF)
          .map(validateCompanyAndReport)

      } yield ()

    new WithApplication(app) {
      Await.result(
        for {
          _ <- setup()
          _ <- sampleDataGenerationTask.runTask()
          _ <- check()
        } yield (),
        Duration.Inf
      )
    }
  }
}
