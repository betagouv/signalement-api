package tasks.report

import cats.implicits.toTraverseOps
import loader.SignalConsoComponents
import models.barcode.gs1.OAuthAccessToken
import models.company.CompanyWithAccess
import models.report.Report
import models.report.ReportStatus
import models.report.sampledata.ProUserGenerator._
import models.report.sampledata.ReportGenerator.sampleGtin
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.Application
import play.api.ApplicationLoader
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.WithApplication
import services.GS1ServiceInterface
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

  val oauthAccessToken = OAuthAccessToken("dummyToken")
  val exampleGs1Response = Json.parse("""
      {
      "isA": [
        "gs1:Offer",
        "s:Offer"
      ],
      "itemOffered": {
        "isA": [
          "gs1:Product",
          "s:Product"
        ],
        "gtin": "3474341105842",
        "brandOwner": {
          "isA": [
            "gs1:Organization",
            "s:Organization"
          ],
          "license": {
            "key": "347434"
          },
          "companyName": "MONBANA",
          "globalLocationNumber": "3014743400109"
        },
        "postalAddress": {
          "isA": "gs1:PostalAddress",
          "postalCode": "53500",
          "addressCountry": "France",
          "addressLocality": {
            "lang": "fr",
            "value": "ERNEE"
          }
        },
        "additionalPartyIdentificationValue": "562100032"
      }
    }
      """)
  val mockGs1Service: GS1ServiceInterface = mock[GS1ServiceInterface]

  mockGs1Service.authenticate(
  ) returns Future.successful(oauthAccessToken)

  mockGs1Service.getProductByGTIN(
    oauthAccessToken,
    sampleGtin
  ) returns Future.successful(Right(Some(exampleGs1Response)))

  class FakeApplicationLoader extends ApplicationLoader {
    var components: SignalConsoComponents = _
    override def load(context: ApplicationLoader.Context): Application = {
      components = new SignalConsoComponents(context) {
        override def gs1Service: GS1ServiceInterface = mockGs1Service
      }
      components.application
    }
  }

  val appLoader                         = new FakeApplicationLoader()
  val app: Application                  = TestApp.buildApp(appLoader)
  val components: SignalConsoComponents = appLoader.components

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

        _ <- userRepository.get(proUserA.id).map(_.isDefined must beTrue)
        _ <- userRepository.get(proUserB.id).map(_.isDefined must beTrue)
        _ <- userRepository.get(proUserC.id).map(_.isDefined must beTrue)
        _ <- userRepository.get(proUserD.id).map(_.isDefined must beTrue)
        _ <- userRepository.get(proUserE.id).map(_.isDefined must beTrue)
        _ <- userRepository.get(proUserF.id).map(_.isDefined must beTrue)

        _ <- companyAccessRepository
          .fetchCompaniesWithLevel(proUserA)
          .map(validateCompanyAndReport)
        _ <- companyAccessRepository
          .fetchCompaniesWithLevel(proUserB)
          .map(validateCompanyAndReport)
        _ <- companyAccessRepository
          .fetchCompaniesWithLevel(proUserC)
          .map(validateCompanyAndReport)
        _ <- companyAccessRepository
          .fetchCompaniesWithLevel(proUserD)
          .map(_.nonEmpty must beTrue)
        _ <- companyAccessRepository
          .fetchCompaniesWithLevel(proUserE)
          .map(validateCompanyAndReport)
        _ <- companyAccessRepository
          .fetchCompaniesWithLevel(proUserF)
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
