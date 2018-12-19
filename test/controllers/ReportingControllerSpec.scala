package controllers

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers, Injecting, WithApplication}
import play.api.libs.json.Json
import repositories.{FileRepository, ReportingRepository}
import services.MailerService

class ReportingControllerSpec(implicit ee: ExecutionEnv) extends Specification with Results with Mockito {

  "ReportingController" should {

    "return a BadRequest with errors if reporting is invalid" in new Context {
      new WithApplication(application) {

        val formData = MultipartFormData[TemporaryFile](
          dataParts = Map("companyType" -> Seq("companyType")),
          files = Seq(),
          badParts = Seq()
        )

        val request = FakeRequest("POST", "/api/reporting").withMultipartFormDataBody(formData)

        val controller = new ReportingController(mock[ReportingRepository], mock[FileRepository], mock[MailerService], mock[Configuration]){
          override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
        }

        val result = route(application, request).get

        status(result) must beEqualTo(BAD_REQUEST)
        //contentAsJson(result) must beEqualTo(Json.obj("errors" -> ""))
      }
    }

  }

  trait Context extends Scope {

    lazy val application = new GuiceApplicationBuilder()
      .configure(Configuration("play.evolutions.enabled" -> false))
      .build()

  }

}
