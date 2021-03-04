package controllers

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.{FakeEnvironment, _}
import models._
import net.codingwell.scalaguice.ScalaModule
import orchestrators.{CompaniesVisibilityOrchestrator, ReportOrchestrator}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.api.{Configuration, Logger}
import repositories._
import services.{MailerService, S3Service, PDFService}
import utils.Constants.ActionEvent._
import utils.Constants.EventType
import utils.Constants.ReportStatus.TRAITEMENT_EN_COURS
import utils.EmailAddress
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv
import utils.Fixtures

import scala.concurrent.Future

class ReportControllerSpec(implicit ee: ExecutionEnv) extends Specification with Results with Mockito {

  val logger: Logger = Logger(this.getClass)

  "return a BadRequest with errors if report is invalid" should {

    "ReportController" in new Context {
      new WithApplication(application) {

        val jsonBody = Json.toJson("category" -> "")

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val controller = new ReportController(mock[ReportOrchestrator], mock[CompanyRepository], mock[ReportRepository], mock[EventRepository], mock[CompaniesVisibilityOrchestrator], mock[S3Service], mock[PDFService], mock[Silhouette[AuthEnv]], mock[Silhouette[APIKeyEnv]], mock[Configuration]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }

        val result = route(application, request).get

        Helpers.status(result) must beEqualTo(BAD_REQUEST)
      }
    }
  }

  "getReportCountBySiret" should {

    val siretFixture = Fixtures.genSiret().sample.get

    "return unauthorized when there no X-Api-Key header" should {

      "ReportController" in new Context {
        new WithApplication(application) {

          val request = FakeRequest("GET", s"/api/ext/reports/siret/$siretFixture/count")
          val controller = application.injector.instanceOf[ReportController]
          val result = route(application, request).get

          Helpers.status(result) must beEqualTo(UNAUTHORIZED)

        }
      }
    }

    "return unauthorized when X-Api-Key header is invalid" should {

      "ReportController" in new Context {
        new WithApplication(application) {

          val request = FakeRequest("GET", s"/api/ext/reports/siret/$siretFixture/count").withHeaders("X-Api-Key" -> "$2a$10$LJ2lIofW2JY.Zyj5BnU0k.BUNn9nFMWBMC45sGbPZOhNRBtkUZg.2")
          val controller = application.injector.instanceOf[ReportController]
          val result = route(application, request).get

          Helpers.status(result) must beEqualTo(UNAUTHORIZED)

        }
      }
    }

    "return report count when X-Api-Key header is valid" should {

      "ReportController" in new Context {
        new WithApplication(application) {

          mockReportRepository.count(Some(siretFixture)) returns Future(5)

          val request = FakeRequest("GET", s"/api/ext/reports/siret/$siretFixture/count").withHeaders("X-Api-Key" -> "$2a$10$nZOeO.LzGe4qsNT9rf4wk.k88oN.P51bLoRVnWOVY0HRsb/NwkFCq")
          val controller = application.injector.instanceOf[ReportController]
          val result = route(application, request).get

          Helpers.status(result) must beEqualTo(OK)
          contentAsJson(result) must beEqualTo(Json.obj("siret" -> siretFixture, "count" -> 5))

        }
      }
    }
  }

  trait Context extends Scope {

    val adminIdentity = Fixtures.genAdminUser.sample.get
    val adminLoginInfo = LoginInfo(CredentialsProvider.ID, adminIdentity.email.value)
    val proIdentity = Fixtures.genProUser.sample.get
    val proLoginInfo = LoginInfo(CredentialsProvider.ID, proIdentity.email.value)

    val companyId = UUID.randomUUID

    implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(adminLoginInfo -> adminIdentity, proLoginInfo -> proIdentity))

    val mockReportRepository = mock[ReportRepository]
    val mockEventRepository = mock[EventRepository]
    val mockCompanyRepository = mock[CompanyRepository]
    val mockAccessTokenRepository = mock[AccessTokenRepository]
    val mockUserRepository = mock[UserRepository]
    val mockMailerService = mock[MailerService]

    mockReportRepository.create(any[Report]) answers { report => Future(report.asInstanceOf[Report]) }
    mockReportRepository.update(any[Report]) answers { report => Future(report.asInstanceOf[Report]) }
    mockReportRepository.attachFilesToReport(any, any[UUID]) returns Future(0)
    mockReportRepository.retrieveReportFiles(any[UUID]) returns Future(Nil)
    mockReportRepository.prefetchReportsFiles(any[List[UUID]]) returns Future(Map.empty)
    mockCompanyRepository.fetchAdminsByCompany(Seq(companyId)) returns Future(Map(companyId -> List(proIdentity)))

    mockUserRepository.create(any[User]) answers {user => Future(user.asInstanceOf[User])}

    mockEventRepository.createEvent(any[Event]) answers { event => Future(event.asInstanceOf[Event]) }

    class FakeModule extends AbstractModule with ScalaModule {
      override def configure() = {
        bind[Environment[AuthEnv]].toInstance(env)
        bind[ReportRepository].toInstance(mockReportRepository)
        bind[EventRepository].toInstance(mockEventRepository)
        bind[CompanyRepository].toInstance(mockCompanyRepository)
        bind[AccessTokenRepository].toInstance(mockAccessTokenRepository)
        bind[UserRepository].toInstance(mockUserRepository)
        bind[MailerService].toInstance(mockMailerService)
      }
    }

    lazy val application = new GuiceApplicationBuilder()
      .configure(
        Configuration(
          "play.evolutions.enabled" -> false,
          "slick.dbs.default.db.connectionPool" -> "disabled",
          "play.mailer.mock" -> true,
          "silhouette.apiKeyAuthenticator.sharedSecret" -> "sharedSecret",
          "play.tmpDirectory" -> "./target"
        )
      )
      .overrides(new FakeModule())
      .build()

  }

}