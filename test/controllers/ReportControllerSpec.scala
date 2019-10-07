package controllers

import java.time.OffsetDateTime
import java.util.UUID

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.{FakeEnvironment, _}
import models._
import net.codingwell.scalaguice.ScalaModule
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Json, Writes}
import play.api.libs.mailer.AttachmentFile
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.api.{Configuration, Logger}
import repositories.{EventFilter, EventRepository, ReportRepository, UserRepository, ReportFilter}
import services.{MailerService, S3Service}
import tasks.ReminderTaskModule
import utils.Constants.ActionEvent._
import utils.Constants.EventType
import utils.Constants.ReportStatus._
import utils.Constants.{ActionEvent, Departments, EventType, ReportStatus}
import utils.silhouette.api.APIKeyEnv
import utils.silhouette.auth.AuthEnv

import scala.concurrent.Future

class ReportControllerSpec(implicit ee: ExecutionEnv) extends Specification with Results with Mockito {

  val logger: Logger = Logger(this.getClass)

  "return a BadRequest with errors if report is invalid" should {

    "ReportController" in new Context {
      new WithApplication(application) {

        val jsonBody = Json.toJson("category" -> "")

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val controller = new ReportController(mock[ReportRepository], mock[EventRepository], mock[UserRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Silhouette[APIKeyEnv]], mock[Configuration], mock[play.api.Environment]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }

        val result = route(application, request).get

        Helpers.status(result) must beEqualTo(BAD_REQUEST)
      }
    }
  }

  "getReportCountBySiret" should {

    val siretFixture = "01232456789"

    "return unauthorized when there no X-Api-Key header" should {

      "ReportController" in new Context {
        new WithApplication(application) {

          val request = FakeRequest("GET", s"/api/reports/siret/$siretFixture/count")
          val controller = application.injector.instanceOf[ReportController]
          val result = route(application, request).get

          Helpers.status(result) must beEqualTo(UNAUTHORIZED)

        }
      }
    }

    "return unauthorized when X-Api-Key header is invalid" should {

      "ReportController" in new Context {
        new WithApplication(application) {

          val request = FakeRequest("GET", s"/api/reports/siret/$siretFixture/count").withHeaders("X-Api-Key" -> "$2a$10$LJ2lIofW2JY.Zyj5BnU0k.BUNn9nFMWBMC45sGbPZOhNRBtkUZg.2")
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

          val request = FakeRequest("GET", s"/api/reports/siret/$siretFixture/count").withHeaders("X-Api-Key" -> "$2a$10$nZOeO.LzGe4qsNT9rf4wk.k88oN.P51bLoRVnWOVY0HRsb/NwkFCq")
          val controller = application.injector.instanceOf[ReportController]
          val result = route(application, request).get

          Helpers.status(result) must beEqualTo(OK)
          contentAsJson(result) must beEqualTo(Json.obj("siret" -> siretFixture, "count" -> 5))

        }
      }.pendingUntilFixed("failed since mockReportRepository injection activate tasks ...")
    }

    "ReportListController" should {

      "generate an export" in new Context {
        new WithApplication(application) {
          val reportId = Some(UUID.fromString("283f76eb-0112-4e9b-a14c-ae2923b5509b"))
          val reportsList = List(
            Report(
              reportId, "foo", List("bar"), List(), "myCompany", "18 rue des Champs",
              None, Some("00000000000000"), Some(OffsetDateTime.now()), "John", "Doe", "jdoe@example.com",
              true, List(), None
            )
          )
          mockReportRepository.getReports(any[Long], any[Int], any[ReportFilter]) returns Future(
            PaginatedResult(1, false, reportsList)
          )
          mockEventRepository.prefetchReportsEvents(reportsList) returns Future(
            Map(reportId.get -> List(
              Event(reportId, reportId, Some(UUID.randomUUID), Some(OffsetDateTime.now()), EventType.DGCCRF, COMMENT, Some(true), None)
            ))
          )
          mockUserRepository.prefetchLogins(List("00000000000000")) returns Future(
            Map("00000000000000" -> proIdentity)
          )

          val request = FakeRequest("GET", s"/api/reports/extract").withAuthenticator[AuthEnv](adminLoginInfo)
          val result = route(application, request).get

          Helpers.status(result) must beEqualTo(OK)
          Helpers.header(Helpers.CONTENT_DISPOSITION, result) must beEqualTo(Some("attachment; filename=\"signalements.xlsx\""))
        }
      }.pendingUntilFixed("failed since mockReportRepository injection activate tasks ...")
    }

  }

  trait Context extends Scope {

    val adminIdentity = User(UUID.randomUUID(),"admin@signalconso.beta.gouv.fr", "password", None, Some("Prénom"), Some("Nom"), Some("admin@signalconso.beta.gouv.fr"), UserRoles.Admin)
    val adminLoginInfo = LoginInfo(CredentialsProvider.ID, adminIdentity.login)
    val proIdentity = User(UUID.randomUUID(),"00000000000000", "password", None, Some("Prénom"), Some("Nom"), Some("pro@signalconso.beta.gouv.fr"), UserRoles.Pro)
    val proLoginInfo = LoginInfo(CredentialsProvider.ID, proIdentity.login)

    implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(adminLoginInfo -> adminIdentity, proLoginInfo -> proIdentity))

    val mockReportRepository = mock[ReportRepository]
    val mockEventRepository = mock[EventRepository]
    val mockUserRepository = mock[UserRepository]
    val mockMailerService = mock[MailerService]

    mockReportRepository.create(any[Report]) answers { report => Future(report.asInstanceOf[Report]) }
    mockReportRepository.update(any[Report]) answers { report => Future(report.asInstanceOf[Report]) }
    mockReportRepository.attachFilesToReport(any, any[UUID]) returns Future(0)
    mockReportRepository.retrieveReportFiles(any[UUID]) returns Future(Nil)

    mockUserRepository.create(any[User]) answers {user => Future(user.asInstanceOf[User])}

    mockEventRepository.createEvent(any[Event]) answers { event => Future(event.asInstanceOf[Event]) }

    class FakeModule extends AbstractModule with ScalaModule {
      override def configure() = {
        bind[Environment[AuthEnv]].toInstance(env)
        //bind[ReportRepository].toInstance(mockReportRepository)
        bind[EventRepository].toInstance(mockEventRepository)
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