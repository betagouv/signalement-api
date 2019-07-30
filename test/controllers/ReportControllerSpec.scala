package controllers

import java.time.LocalDateTime
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
import play.api.{Configuration, Logger}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.api.libs.mailer.{Attachment, AttachmentFile}
import repositories.{EventFilter, EventRepository, ReportRepository, UserRepository}
import services.{MailerService, S3Service}
import tasks.TasksModule
import utils.Constants.ActionEvent._
import utils.Constants.EventType.{CONSO, EventTypeValue, PRO}
import utils.Constants.StatusConso._
import utils.Constants.{ActionEvent, Departments, EventType, StatusPro}
import utils.Constants.StatusPro._
import utils.silhouette.AuthEnv

import scala.concurrent.Future
import scala.util.Random
class ReportControllerSpec(implicit ee: ExecutionEnv) extends Specification with Results with Mockito {

  val logger: Logger = Logger(this.getClass)

  "return a BadRequest with errors if report is invalid" should {

    "ReportController" in new Context {
      new WithApplication(application) {

        val jsonBody = Json.toJson("category" -> "")

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val controller = new ReportController(mock[ReportRepository], mock[EventRepository], mock[UserRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Configuration], mock[play.api.Environment]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }

        val result = route(application, request).get

        Helpers.status(result) must beEqualTo(BAD_REQUEST)
      }
    }
  }

  "determineStatusPro" should {

    "ReportController" in new Context {
      new WithApplication(application) {

        val controller = new ReportController(mock[ReportRepository], mock[EventRepository], mock[UserRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Configuration], mock[play.api.Environment]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }
        val reportFixture = Report(None, "category", List.empty, List.empty, "companyName", "companyAddress", None, None, None, "firsName", "lastName", "email", true, List.empty, None, None)

        controller.determineStatusPro(reportFixture.copy(companyPostalCode = Some("45500"))) must equalTo(A_TRAITER)
        controller.determineStatusPro(reportFixture.copy(companyPostalCode = Some("51500"))) must equalTo(NA)

      }
    }
  }

  "determineStatusPro with event" should {

    "ReportController" in new Context {
      new WithApplication(application) {

        val controller = new ReportController(mock[ReportRepository], mock[EventRepository], mock[UserRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Configuration], mock[play.api.Environment]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }

        val fakeUUID = UUID.randomUUID()
        val fakeTime = LocalDateTime.now()

        val eventFixture = Event(Some(fakeUUID), Some(fakeUUID), fakeUUID, Some(fakeTime), PRO, A_CONTACTER, Some(true), None)
        controller.determineStatusPro(eventFixture.copy(action = A_CONTACTER), Some(NA)) must equalTo(A_TRAITER)
        controller.determineStatusPro(eventFixture.copy(action = HORS_PERIMETRE), Some(NA)) must equalTo(NA)
        controller.determineStatusPro(eventFixture.copy(action = CONTACT_EMAIL), Some(NA)) must equalTo(TRAITEMENT_EN_COURS)
        controller.determineStatusPro(eventFixture.copy(action = CONTACT_COURRIER), Some(NA)) must equalTo(TRAITEMENT_EN_COURS)
        controller.determineStatusPro(eventFixture.copy(action = REPONSE_PRO_CONTACT, resultAction = Some(true)), Some(NA)) must equalTo(A_TRANSFERER_SIGNALEMENT)
        controller.determineStatusPro(eventFixture.copy(action = REPONSE_PRO_CONTACT, resultAction = Some(false)), Some(NA)) must equalTo(SIGNALEMENT_REFUSE)
        controller.determineStatusPro(eventFixture.copy(action = REPONSE_PRO_SIGNALEMENT, resultAction = Some(true)), Some(NA)) must equalTo(PROMESSE_ACTION)
        controller.determineStatusPro(eventFixture.copy(action = REPONSE_PRO_SIGNALEMENT, resultAction = Some(false)), Some(NA)) must equalTo(PROMESSE_ACTION_REFUSEE)
        controller.determineStatusPro(eventFixture.copy(action = EMAIL_TRANSMISSION, resultAction = Some(false)), Some(TRAITEMENT_EN_COURS)) must equalTo(TRAITEMENT_EN_COURS)
        controller.determineStatusPro(eventFixture.copy(action = RETOUR_COURRIER), Some(TRAITEMENT_EN_COURS)) must equalTo(ADRESSE_INCORRECTE)
      }
    }
  }

  "determineStatusConso with event" should {

    "ReportController" in new Context {
      new WithApplication(application) {

        val controller = new ReportController(mock[ReportRepository], mock[EventRepository], mock[UserRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Configuration], mock[play.api.Environment]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }

        val fakeUUID = UUID.randomUUID()
        val fakeTime = LocalDateTime.now()

        val eventFixture = Event(Some(fakeUUID), Some(fakeUUID), fakeUUID, Some(fakeTime), CONSO, EMAIL_AR, Some(true), Some(EN_ATTENTE.value))
        controller.determineStatusConso(eventFixture.copy(action = A_CONTACTER), Some(EN_ATTENTE)) must equalTo(EN_ATTENTE)
        controller.determineStatusConso(eventFixture.copy(action = HORS_PERIMETRE), Some(EN_ATTENTE)) must equalTo(A_RECONTACTER)
        controller.determineStatusConso(eventFixture.copy(action = CONTACT_COURRIER), Some(EN_ATTENTE)) must equalTo(EN_ATTENTE)
        controller.determineStatusConso(eventFixture.copy(action = REPONSE_PRO_CONTACT), Some(EN_ATTENTE)) must equalTo(EN_ATTENTE)
        controller.determineStatusConso(eventFixture.copy(action = ENVOI_SIGNALEMENT), Some(EN_ATTENTE)) must equalTo(A_INFORMER_TRANSMISSION)
        controller.determineStatusConso(eventFixture.copy(action = EMAIL_TRANSMISSION), Some(A_INFORMER_TRANSMISSION)) must equalTo(EN_ATTENTE)
        controller.determineStatusConso(eventFixture.copy(action = REPONSE_PRO_SIGNALEMENT), Some(EN_ATTENTE)) must equalTo(A_INFORMER_REPONSE_PRO)
        controller.determineStatusConso(eventFixture.copy(action = EMAIL_REPONSE_PRO), Some(A_INFORMER_REPONSE_PRO)) must equalTo(FAIT)
        controller.determineStatusConso(eventFixture.copy(action = EMAIL_NON_PRISE_EN_COMPTE), Some(A_RECONTACTER)) must equalTo(FAIT)
        controller.determineStatusConso(eventFixture.copy(action = A_CONTACTER), None) must equalTo(EN_ATTENTE)

        controller.determineStatusConso(eventFixture.copy(action = REPONSE_PRO_CONTACT), Some(A_RECONTACTER)) must equalTo(A_RECONTACTER)
      }
    }
  }

  "createReport when the report concerns a professional in an authorized department" should {

    val reportUUID = UUID.randomUUID()
    val reportFixture = Report(
      None, "category", List("subcategory"), List(), "companyName", "companyAddress", Some(Departments.AUTHORIZED(0)), Some("00000000000000"), Some(LocalDateTime.now()),
      "firstName", "lastName", "email", true, List(), None, None
    )

    "if the professional has no account :" +
      "- create the report" +
      "- send an acknowledgment mail to the consummer" +
      "- create an account for the professional" +
      "- return the report" should {

      "ReportController" in new Context {

        new WithApplication(application) {

          mockReportRepository.create(any[Report]) returns Future(reportFixture.copy(id = Some(reportUUID)))

          mockUserRepository.findByLogin(reportFixture.companySiret.get) returns Future(None)
          mockUserRepository.create(any[User]) answers {user => Future(user.asInstanceOf[User])}

          val controller = application.injector.instanceOf[ReportController]
          val result = controller.createReport().apply(FakeRequest().withBody(Json.toJson(reportFixture)))

          Helpers.status(result) must beEqualTo(OK)
          contentAsJson(result) must equalTo(Json.toJson(reportFixture.copy(id = Some(reportUUID))))

          there was one(mockReportRepository).create(any[Report])
          there was one(mockMailerService)
            .sendEmail(application.configuration.get[String]("play.mail.from"), reportFixture.email)(
              "Votre signalement",
              views.html.mails.consumer.reportAcknowledgment(reportFixture, application.configuration.get[String]("play.mail.contactRecipient"), Nil).toString,
              Seq(AttachmentFile("logo-signal-conso.png", application.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))
          there was one(mockUserRepository).create(any[User])
        }
      }
    }

    "if the professional has already an account :" +
      "- create the report" +
      "- send an acknowledgment mail to the consummer" +
      "- send a notification mail to the professional" +
      "- return the report" should {

      "ReportController" in new Context {

        new WithApplication(application) {

          mockReportRepository.create(any[Report]) returns Future(reportFixture.copy(id = Some(reportUUID)))

          mockUserRepository.findByLogin(reportFixture.companySiret.get) returns Future(Some(proIdentity))

          val controller = application.injector.instanceOf[ReportController]
          val result = controller.createReport().apply(FakeRequest().withBody(Json.toJson(reportFixture)))

          Helpers.status(result) must beEqualTo(OK)
          contentAsJson(result) must equalTo(Json.toJson(reportFixture.copy(id = Some(reportUUID))))

          there was no(mockUserRepository).create(any[User])
          there was one(mockReportRepository).create(any[Report])
          there was one(mockMailerService)
            .sendEmail(application.configuration.get[String]("play.mail.from"), reportFixture.email)(
              "Votre signalement",
              views.html.mails.consumer.reportAcknowledgment(reportFixture, application.configuration.get[String]("play.mail.contactRecipient"), Nil).toString,
              Seq(AttachmentFile("logo-signal-conso.png", application.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))
          there was one(mockMailerService)
            .sendEmail(application.configuration.get[String]("play.mail.from"), proIdentity.email.get)(
              "Nouveau signalement",
              views.html.mails.professional.reportNotification(reportFixture).toString,
              Seq(AttachmentFile("logo-signal-conso.png", application.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))
        }
      }
    }

  }

  "getReport" should {

    val reportUUID = UUID.randomUUID()
    val reportFixture = Report(
      Some(reportUUID), "category", List("subcategory"), List(), "companyName", "companyAddress", None, Some("00000000000000"), Some(LocalDateTime.now()),
      "firstName", "lastName", "email", true, List(), None, None
    )

    "return the report when the user is an admin" should {

      "ReportController" in new Context {
        new WithApplication(application) {

          mockReportRepository.getReport(reportUUID) returns Future(Some(reportFixture))

          val controller = application.injector.instanceOf[ReportController]
          val result = controller.getReport(reportUUID.toString).apply(FakeRequest().withAuthenticator[AuthEnv](adminLoginInfo))

          Helpers.status(result) must beEqualTo(OK)
          contentAsJson(result) must equalTo(Json.toJson(reportFixture))
        }
      }
    }

    "return Unauthorized when the user is a professional not concerned by the report" should {

      "ReportController" in new Context {
        new WithApplication(application) {

          mockReportRepository.getReport(reportUUID) returns Future(Some(reportFixture.copy(companySiret = Some("11111111111111"))))

          val controller = application.injector.instanceOf[ReportController]
          val result = controller.getReport(reportUUID.toString).apply(FakeRequest().withAuthenticator[AuthEnv](proLoginInfo))

          Helpers.status(result) must beEqualTo(UNAUTHORIZED)
        }
      }
    }

    "return the report when the user is professional who has already got it " should {

      "ReportController" in new Context {
        new WithApplication(application) {

          mockReportRepository.getReport(reportUUID) returns Future(Some(reportFixture.copy(statusPro = Some(StatusPro.SIGNALEMENT_TRANSMIS))))
          mockEventRepository.getEvents(reportUUID, EventFilter(None)) returns Future(
            List(Event(Some(UUID.randomUUID()), Some(reportUUID), proIdentity.id, Some(LocalDateTime.now()), EventType.PRO, ActionEvent.ENVOI_SIGNALEMENT, Some(true), None))
          )

          val controller = application.injector.instanceOf[ReportController]
          val result = controller.getReport(reportUUID.toString).apply(FakeRequest().withAuthenticator[AuthEnv](proLoginInfo))

          Helpers.status(result) must beEqualTo(OK)
          implicit val reportWriter = Report.reportProWriter
          contentAsJson(result) must equalTo(Json.toJson(Some(reportFixture.copy(statusPro = Some(StatusPro.SIGNALEMENT_TRANSMIS)))))
        }
      }
    }

    "when the user is a professional who get it for the first time :" +
      "- notify the consumer by mail about the report consultation" +
      "- return the report with modified status" should {

      "ReportController" in new Context {
        new WithApplication(application) {

          mockReportRepository.getReport(reportUUID) returns Future(Some(reportFixture.copy(statusPro = Some(StatusPro.A_TRAITER))))
          mockEventRepository.getEvents(reportUUID, EventFilter(None)) returns Future(List())

          mockEventRepository.createEvent(any[Event]) answers { event => Future(event.asInstanceOf[Event]) }
          mockReportRepository.update(any[Report]) answers { report => Future(report.asInstanceOf[Report]) }

          val controller = application.injector.instanceOf[ReportController]
          val result = controller.getReport(reportUUID.toString).apply(FakeRequest().withAuthenticator[AuthEnv](proLoginInfo))

          Helpers.status(result) must beEqualTo(OK)
          implicit val reportWriter = Report.reportProWriter
          contentAsJson(result) must equalTo(Json.toJson(reportFixture.copy(statusPro = Some(StatusPro.SIGNALEMENT_TRANSMIS))))

          there was one(mockMailerService)
            .sendEmail(application.configuration.get[String]("play.mail.from"), reportFixture.email)(
              "Votre signalement",
              views.html.mails.consumer.reportTransmission(reportFixture).toString,
              Seq(AttachmentFile("logo-signal-conso.png", application.environment.getFile("/appfiles/logo-signal-conso.png"), contentId = Some("logo"))))
        }
      }

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

    mockReportRepository.attachFilesToReport(any, any[UUID]) returns Future(0)
    mockReportRepository.retrieveReportFiles(any[UUID]) returns Future(Nil)

    class FakeModule extends AbstractModule with ScalaModule {
      override def configure() = {
        bind[Environment[AuthEnv]].toInstance(env)
        bind[ReportRepository].toInstance(mockReportRepository)
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
          "play.mailer.mock" -> true
        )
      )
      .disable[TasksModule]
      .overrides(new FakeModule())
      .build()

  }

}