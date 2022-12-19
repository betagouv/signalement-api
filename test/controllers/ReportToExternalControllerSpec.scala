package controllers

import loader.SignalConsoComponents
import models.{Consumer, PaginatedResult}
import models.report.{Report, ReportFile, ReportFilter}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.{Application, ApplicationLoader, Configuration, Logger}
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import repositories.report.{ReportRepository, ReportRepositoryInterface}
import repositories.reportfile.ReportFileRepositoryInterface
import utils.{AppSpec, Fixtures, TestApp}

import java.util.UUID
import scala.collection.SortedMap
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

class ReportToExternalControllerSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with Mockito {

  val logger: Logger = Logger(this.getClass)

  override def afterAll(): Unit = ()

  "getReportCountBySiret" should {
    val siretFixture = Fixtures.genSiret().sample.get

    "return unauthorized when there no X-Api-Key header" should {

      "ReportController1" in new Context {
        new WithApplication(app) {
          val request = FakeRequest("GET", s"/api/ext/reports?siret=$siretFixture")
          val result = route(app, request).get
          Helpers.status(result) must beEqualTo(UNAUTHORIZED)
        }
      }
    }

    "return unauthorized when X-Api-Key header is invalid" should {

      "ReportController2" in new Context {
        new WithApplication(app) {
          val request = FakeRequest("GET", s"/api/ext/reports?siret=$siretFixture").withHeaders(
            "X-Api-Key" -> "invalid_key"
          )
          val result = route(app, request).get
          Helpers.status(result) must beEqualTo(UNAUTHORIZED)
        }
      }
    }

    "return report count when X-Api-Key header is valid" should {

      "ReportController3" in new Context {
        new WithApplication(app) {

          Await.result(
            for {
              _ <- components.consumerRepository.create(
                Consumer(name = "test", apiKey = components.passwordHasherRegistry.current.hash("test").password)
              )
            } yield (),
            Duration.Inf
          )

          val request = FakeRequest("GET", s"/api/ext/reports?siret=$siretFixture").withHeaders(
            "X-Api-Key" -> "test"
          )
          val result = route(app, request).get
          Helpers.status(result) must beEqualTo(OK)
        }
      }
    }
  }

  trait Context extends Scope {

    val companyId = UUID.randomUUID

    val mockReportRepository: ReportRepositoryInterface = mock[ReportRepositoryInterface]
    val mockReportFileRepository: ReportFileRepositoryInterface = mock[ReportFileRepositoryInterface]

    implicit val ordering: ReportRepository.ReportFileOrdering.type = ReportRepository.ReportFileOrdering

    mockReportRepository.getReports(any[ReportFilter], any[Option[Long]], any[Option[Int]]) returns Future(
      PaginatedResult(0, false, List())
    )
    mockReportRepository.getReportsWithFiles(any[ReportFilter]) returns Future(
      SortedMap.empty[Report, List[ReportFile]]
    )
    mockReportFileRepository.prefetchReportsFiles(any[List[UUID]]) returns Future(Map())

    class FakeApplicationLoader extends ApplicationLoader {
      var components: SignalConsoComponents = _

      override def load(context: ApplicationLoader.Context): Application = {
        components = new SignalConsoComponents(context) {
          override def reportRepository: ReportRepositoryInterface = mockReportRepository
          override def reportFileRepository: ReportFileRepositoryInterface = mockReportFileRepository
          override def configuration: Configuration = super.configuration
        }
        components.application
      }

    }

    val appLoader = new FakeApplicationLoader()
    val app: Application = TestApp.buildApp(appLoader)
    val components: SignalConsoComponents = appLoader.components

  }
}
