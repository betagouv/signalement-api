package controllers

import java.util.UUID
import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.FakeEnvironment
import config.EmailConfiguration
import config.SignalConsoConfiguration
import config.TokenConfiguration
import config.UploadConfiguration
import controllers.error.AppError.InvalidEmail
import controllers.error.ErrorPayload
import net.codingwell.scalaguice.ScalaModule
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.api.Configuration
import play.api.Logger
import services.MailerService
import utils.silhouette.auth.AuthEnv
import utils.EmailAddress
import utils.Fixtures

import java.net.URI
import java.time.Period

class ReportControllerSpec(implicit ee: ExecutionEnv) extends Specification with Results with Mockito {

  val logger: Logger = Logger(this.getClass)

  "ReportController" should {

    "return a BadRequest with errors if report is invalid" in new Context {
      val app = application()
      new WithApplication(app) {

        val jsonBody = Json.toJson("category" -> "")

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val result = route(app, request).get

        Helpers.status(result) must beEqualTo(BAD_REQUEST)
      }
    }

    "return a BadRequest on invalid email" in new Context {
      val app = application()
      new WithApplication(app) {

        val draftReport = Fixtures.genDraftReport.sample
        val jsonBody = Json.toJson(draftReport)

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val result = route(app, request).get

        Helpers.status(result) must beEqualTo(BAD_REQUEST)
        Helpers.contentAsJson(result) must beEqualTo(
          Json.toJson(ErrorPayload(InvalidEmail(draftReport.get.email.value)))
        )
      }
    }

    "block spammed email" in new Context {
      val blockedEmail = "spammer@gmail.com"
      val app = application(skipValidation = true, List(blockedEmail))
      new WithApplication(app) {

        val draftReport = Fixtures.genDraftReport.sample.get.copy(email = EmailAddress(blockedEmail))
        val jsonBody = Json.toJson(draftReport)

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val result = route(app, request).get
        Helpers.status(result) must beEqualTo(OK)

        Helpers.contentAsBytes(result).isEmpty mustEqual true

      }
    }

  }

  trait Context extends Scope {

    val adminIdentity = Fixtures.genAdminUser.sample.get
    val adminLoginInfo = LoginInfo(CredentialsProvider.ID, adminIdentity.email.value)
    val proIdentity = Fixtures.genProUser.sample.get
    val proLoginInfo = LoginInfo(CredentialsProvider.ID, proIdentity.email.value)

    val companyId = UUID.randomUUID

    implicit val env: Environment[AuthEnv] =
      new FakeEnvironment[AuthEnv](Seq(adminLoginInfo -> adminIdentity, proLoginInfo -> proIdentity))

    val mockMailerService = mock[MailerService]

    class FakeModule(skipValidation: Boolean, spammerBlacklist: List[String]) extends AbstractModule with ScalaModule {
      override def configure() = {
        bind[Environment[AuthEnv]].toInstance(env)
        bind[MailerService].toInstance(mockMailerService)
        bind[EmailConfiguration].toInstance(
          EmailConfiguration(EmailAddress("test@sc.com"), EmailAddress("test@sc.com"), skipValidation, "", List(""))
        )

        val tokenConfiguration = TokenConfiguration(None, None, None, Period.ZERO)
        val uploadConfiguration = UploadConfiguration(Seq.empty, false)

        bind[SignalConsoConfiguration].toInstance(
          SignalConsoConfiguration(
            "",
            new URI("http://test.com"),
            new URI("http://test.com"),
            new URI("http://test.com"),
            tokenConfiguration,
            uploadConfiguration,
            spammerBlacklist
          )
        )
      }
    }

    def application(skipValidation: Boolean = false, spammerBlacklist: List[String] = List.empty) =
      new GuiceApplicationBuilder()
        .configure(
          Configuration(
            "play.evolutions.enabled" -> false,
            "slick.dbs.default.db.connectionPool" -> "disabled",
            "play.mailer.mock" -> true,
            "skip-report-email-validation" -> true,
            "silhouette.apiKeyAuthenticator.sharedSecret" -> "sharedSecret",
            "play.tmpDirectory" -> "./target"
          )
        )
        .overrides(new FakeModule(skipValidation, spammerBlacklist))
        .build()

  }

}
