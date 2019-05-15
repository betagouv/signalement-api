package controllers

import java.time.LocalDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import models.{Event, Report}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.{Configuration, Environment}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers, WithApplication}
import repositories.{ReportRepository, UserRepository}
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.EventType.{CONSO, PRO}
import utils.Constants.StatusConso.{A_INFORMER_REPONSE_PRO, A_INFORMER_TRANSMISSION, A_RECONTACTER, EN_ATTENTE, FAIT}
import utils.Constants.StatusPro._
import utils.silhouette.AuthEnv

class ReportControllerSpec(implicit ee: ExecutionEnv) extends Specification with Results with Mockito {

  "ReportController" should {

    "return a BadRequest with errors if report is invalid" in new Context {
      new WithApplication(application) {

        val jsonBody = Json.toJson("category" -> "")

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val controller = new ReportController(mock[ReportRepository], mock[UserRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Configuration], mock[Environment]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }

        val result = route(application, request).get

        Helpers.status(result) must beEqualTo(BAD_REQUEST)
        //contentAsJson(result) must beEqualTo(Json.obj("errors" -> ""))
      }
    }

    "determineStatusPro" in new Context {
      new WithApplication(application) {

        val controller = new ReportController(mock[ReportRepository], mock[UserRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Configuration], mock[Environment]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }
        val reportFixture = Report(None, "category", List.empty, List.empty, "companyName", "companyAddress", None, None, None, "firsName", "lastName", "email", true, List.empty, None, None)

        controller.determineStatusPro(reportFixture.copy(companyPostalCode = Some("45500"))) must equalTo(A_TRAITER)
        controller.determineStatusPro(reportFixture.copy(companyPostalCode = Some("51500"))) must equalTo(NA)

      }
    }

    "determineStatusPro with event" in new Context {
      new WithApplication(application) {

        val controller = new ReportController(mock[ReportRepository], mock[UserRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Configuration], mock[Environment]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }

        val fakeUUID = UUID.randomUUID()
        val fakeTime = LocalDateTime.now()

        val eventFixture = Event(Some(fakeUUID), Some(fakeUUID), fakeUUID, Some(fakeTime), PRO, A_CONTACTER, Some(true), None)
        controller.determineStatusPro(eventFixture.copy(action = A_CONTACTER), Some(NA.value)) must equalTo(A_TRAITER)
        controller.determineStatusPro(eventFixture.copy(action = HORS_PERIMETRE), Some(NA.value)) must equalTo(NA)
        controller.determineStatusPro(eventFixture.copy(action = CONTACT_EMAIL), Some(NA.value)) must equalTo(TRAITEMENT_EN_COURS)
        controller.determineStatusPro(eventFixture.copy(action = CONTACT_COURRIER), Some(NA.value)) must equalTo(TRAITEMENT_EN_COURS)
        controller.determineStatusPro(eventFixture.copy(action = REPONSE_PRO_CONTACT, resultAction = Some(true)), Some(NA.value)) must equalTo(A_TRANSFERER_SIGNALEMENT)
        controller.determineStatusPro(eventFixture.copy(action = REPONSE_PRO_CONTACT, resultAction = Some(false)), Some(NA.value)) must equalTo(SIGNALEMENT_REFUSE)
        controller.determineStatusPro(eventFixture.copy(action = REPONSE_PRO_SIGNALEMENT, resultAction = Some(true)), Some(NA.value)) must equalTo(PROMESSE_ACTION)
        controller.determineStatusPro(eventFixture.copy(action = REPONSE_PRO_SIGNALEMENT, resultAction = Some(false)), Some(NA.value)) must equalTo(PROMESSE_ACTION_REFUSEE)
        controller.determineStatusPro(eventFixture.copy(action = EMAIL_TRANSMISSION, resultAction = Some(false)), Some(TRAITEMENT_EN_COURS.value)) must equalTo(TRAITEMENT_EN_COURS)

      }
    }

    "determineStatusConso with event" in new Context {
      new WithApplication(application) {

        val controller = new ReportController(mock[ReportRepository], mock[UserRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Configuration], mock[Environment]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }

        val fakeUUID = UUID.randomUUID()
        val fakeTime = LocalDateTime.now()

        val eventFixture = Event(Some(fakeUUID), Some(fakeUUID), fakeUUID, Some(fakeTime), CONSO, EMAIL_AR, Some(true), Some(EN_ATTENTE.value))
        controller.determineStatusConso(eventFixture.copy(action = A_CONTACTER), Some(EN_ATTENTE.value)) must equalTo(EN_ATTENTE)
        controller.determineStatusConso(eventFixture.copy(action = HORS_PERIMETRE), Some(EN_ATTENTE.value)) must equalTo(EN_ATTENTE)
        controller.determineStatusConso(eventFixture.copy(action = CONTACT_COURRIER), Some(EN_ATTENTE.value)) must equalTo(EN_ATTENTE)
        controller.determineStatusConso(eventFixture.copy(action = REPONSE_PRO_CONTACT), Some(EN_ATTENTE.value)) must equalTo(EN_ATTENTE)
        controller.determineStatusConso(eventFixture.copy(action = ENVOI_SIGNALEMENT), Some(EN_ATTENTE.value)) must equalTo(A_INFORMER_TRANSMISSION)
        controller.determineStatusConso(eventFixture.copy(action = EMAIL_TRANSMISSION), Some(A_INFORMER_TRANSMISSION.value)) must equalTo(EN_ATTENTE)
        controller.determineStatusConso(eventFixture.copy(action = REPONSE_PRO_SIGNALEMENT), Some(EN_ATTENTE.value)) must equalTo(A_INFORMER_REPONSE_PRO)
        controller.determineStatusConso(eventFixture.copy(action = EMAIL_REPONSE_PRO), Some(A_INFORMER_REPONSE_PRO.value)) must equalTo(FAIT)
        controller.determineStatusConso(eventFixture.copy(action = EMAIL_NON_PRISE_EN_COMPTE), Some(A_RECONTACTER.value)) must equalTo(FAIT)

      }
    }
  }

  trait Context extends Scope {

    lazy val application = new GuiceApplicationBuilder()
      .configure(Configuration("play.evolutions.enabled" -> false))
      .build()

  }

}