package controllers

import java.time.LocalDateTime
import java.util.UUID

import com.mohiva.play.silhouette.api.Silhouette
import models.{DetailInputValue, Event, File, Report}
import models.DetailInputValue.string2detailInputValue
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
import repositories.ReportRepository
import services.{MailerService, S3Service}
import utils.Constants.ActionEvent._
import utils.Constants.EventType.PRO
import utils.Constants.StatusPro._
import utils.silhouette.AuthEnv

class ReportControllerSpec(implicit ee: ExecutionEnv) extends Specification with Results with Mockito {

  "ReportController" should {

    "return a BadRequest with errors if report is invalid" in new Context {
      new WithApplication(application) {

        val jsonBody = Json.toJson("category" -> "")

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val controller = new ReportController(mock[ReportRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Configuration], mock[Environment]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }

        val result = route(application, request).get

        Helpers.status(result) must beEqualTo(BAD_REQUEST)
        //contentAsJson(result) must beEqualTo(Json.obj("errors" -> ""))
      }
    }

    "determineStatusPro" in new Context {
      new WithApplication(application) {

        val controller = new ReportController(mock[ReportRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Configuration], mock[Environment]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }
        val reportFixture = Report(None, "category", List.empty, List.empty, "companyName", "companyAddress", None, None, None, "firsName", "lastName", "email", true, List.empty, None)

        controller.determineStatusPro(reportFixture.copy(companyPostalCode = Some("45500"))) must equalTo(Some(A_TRAITER))
        controller.determineStatusPro(reportFixture.copy(companyPostalCode = Some("51500"))) must equalTo(Some(NA))

      }
    }

    "determineStatusPro with event" in new Context {
      new WithApplication(application) {

        val controller = new ReportController(mock[ReportRepository], mock[MailerService], mock[S3Service], mock[Silhouette[AuthEnv]], mock[Configuration], mock[Environment]) {
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }

        val fakeUUID = UUID.randomUUID()
        val fakeTime = LocalDateTime.now()

        val eventFixture = Event(Some(fakeUUID), fakeUUID, fakeUUID, Some(fakeTime), PRO, A_CONTACTER, Some("OK"), None)
        controller.determineStatusPro(eventFixture.copy(action = A_CONTACTER)) must equalTo(A_TRAITER)
        controller.determineStatusPro(eventFixture.copy(action = HORS_PERIMETRE)) must equalTo(NA)
        controller.determineStatusPro(eventFixture.copy(action = CONTACT_EMAIL)) must equalTo(TRAITEMENT_EN_COURS)
        controller.determineStatusPro(eventFixture.copy(action = CONTACT_COURRIER)) must equalTo(TRAITEMENT_EN_COURS)
        controller.determineStatusPro(eventFixture.copy(action = REPONSE_PRO_SIGNALEMENT, resultAction = Some("OK"))) must equalTo(PROMESSE_ACTION)
        controller.determineStatusPro(eventFixture.copy(action = REPONSE_PRO_SIGNALEMENT, resultAction = Some("KO"))) must equalTo(SIGNALEMENT_REFUSE)

      }
    }

  }

    trait Context extends Scope {

      lazy val application = new GuiceApplicationBuilder()
        .configure(Configuration("play.evolutions.enabled" -> false))
        .build()

    }

  }

