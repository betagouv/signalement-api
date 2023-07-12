package controllers

import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.FakeEnvironment
import com.mohiva.play.silhouette.test.FakeRequestWithAuthenticator
import config.EmailConfiguration
import config.SignalConsoConfiguration
import config.TokenConfiguration
import config.UploadConfiguration
import controllers.error.AppError.InvalidEmail
import controllers.error.ErrorPayload
import loader.SignalConsoComponents
import models.BlacklistedEmail
import models.report.ReportFile
import models.report.ReportFileOrigin
import models.report.reportfile.ReportFileId
import models.report.review.ResponseConsumerReview
import models.report.review.ResponseConsumerReviewId
import models.report.review.ResponseEvaluation.Positive
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Application
import play.api.ApplicationLoader
import play.api.Configuration
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers._
import play.api.test._
import services.MailRetriesService
import services.S3ServiceInterface
import utils.Constants.ActionEvent.POST_ACCOUNT_ACTIVATION_DOC
import utils.Constants.EventType
import utils.EmailAddress
import utils.Fixtures
import utils.S3ServiceMock
import utils.TestApp
import utils.silhouette.auth.AuthEnv

import java.time.temporal.ChronoUnit
import java.net.URI
import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

class ReportControllerSpec(implicit ee: ExecutionEnv) extends Specification with Results with Mockito {

  val logger: Logger = Logger(this.getClass)

  "ReportController" should {

    "return a BadRequest with errors if report is invalid" in new Context {
      val testEnv = application()
      import testEnv._

      new WithApplication(app) {

        val jsonBody = Json.toJson("category" -> "")

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val result = route(app, request).get

        Helpers.status(result) must beEqualTo(BAD_REQUEST)
      }
    }

    "return a BadRequest on invalid email" in new Context {
      val testEnv = application()
      import testEnv._

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

    "block spammed email but return normally" in new Context {
      val blockedEmail = "spammer@gmail.com"
      val testEnv = application(skipValidation = true)
      import testEnv._

      new WithApplication(app) {

        Await.result(
          for {
            _ <- blacklistedEmailsRepository.create(
              BlacklistedEmail(
                email = blockedEmail,
                comments = ""
              )
            )
          } yield (),
          Duration.Inf
        )

        val draftReport = Fixtures.genDraftReport.sample.get.copy(email = EmailAddress(blockedEmail))
        val jsonBody = Json.toJson(draftReport)

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val result = route(app, request).get
        Helpers.status(result) must beEqualTo(OK)
        (Helpers.contentAsJson(result) \ "id").as[String] must not(beEmpty)
        (Helpers.contentAsJson(result) \ "email").as[String] must beEqualTo(blockedEmail)
      }
    }

    "delete report" in new Context {

      val testEnv = application()
      import testEnv._

      new WithApplication(app) {

        val company = Fixtures.genCompany.sample.get
        val report = Fixtures.genReportForCompany(company).sample.get
        val event = Fixtures.genEventForReport(report.id, EventType.PRO, POST_ACCOUNT_ACTIVATION_DOC).sample.get
        val reportFile = ReportFile(
          ReportFileId.generateId(),
          Some(report.id),
          OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
          "fileName",
          "storageName",
          ReportFileOrigin(""),
          None
        )
        val review =
          ResponseConsumerReview(
            ResponseConsumerReviewId.generateId(),
            report.id,
            Positive,
            OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
            None
          )

        Await.result(
          for {
            _ <- companyRepository.create(company)
            _ <- reportRepository.create(report)
            _ <- reportFileRepository.create(reportFile)
            _ <- eventRepository.create(event)
            _ <- responseConsumerReviewRepository.create(review)
          } yield (),
          Duration.Inf
        )

        val request =
          FakeRequest("DELETE", s"/api/reports/${report.id.toString}").withAuthenticator[AuthEnv](adminLoginInfo)
        val result = route(app, request).get

        Helpers.status(result) must beEqualTo(NO_CONTENT)
        Helpers.contentAsBytes(result).isEmpty mustEqual true

        val (maybeReport, maybeReportFile, maybeEvent, maybeReview) = Await.result(
          for {
            maybeReport <- reportRepository.get(report.id)
            maybeReportFile <- reportFileRepository.get(reportFile.id)
            maybeEvent <- eventRepository.get(event.id)
            maybeReview <- responseConsumerReviewRepository.get(review.id)
          } yield (maybeReport, maybeReportFile, maybeEvent, maybeReview),
          Duration.Inf
        )
        maybeReport must beNone
        maybeReportFile must beNone
        maybeEvent must beNone
        maybeReview must beNone

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

    val mailRetriesService = mock[MailRetriesService]
    val mockS3Service = new S3ServiceMock()

    def application(skipValidation: Boolean = false) = new {

      class FakeApplicationLoader(skipValidation: Boolean = false) extends ApplicationLoader {
        var components: SignalConsoComponents = _

        override def load(context: ApplicationLoader.Context): Application = {
          components = new SignalConsoComponents(context) {

            override def authEnv: Environment[AuthEnv] = env
            override def configuration: Configuration = Configuration(
              "slick.dbs.default.db.connectionPool" -> "disabled",
              "play.mailer.mock" -> true,
              "skip-report-email-validation" -> true,
              "silhouette.authenticator.sharedSecret" -> "sharedSecret",
              "play.tmpDirectory" -> "./target"
            ).withFallback(
              super.configuration
            )

            override def s3Service: S3ServiceInterface = mockS3Service
            override def tokenConfiguration =
              TokenConfiguration(None, None, 12.hours, Period.ofDays(60), Period.ZERO, None)
            override def uploadConfiguration = UploadConfiguration(Seq.empty, false, "/tmp")
//            override def mobileAppConfiguration = MobileAppConfiguration(
//              minimumAppVersionIos = "1.0.0",
//              minimumAppVersionAndroid = "1.0.0"
//            )
            override def signalConsoConfiguration: SignalConsoConfiguration =
              SignalConsoConfiguration(
                "",
                new URI("http://test.com"),
                new URI("http://test.com"),
                new URI("http://test.com"),
                tokenConfiguration,
                uploadConfiguration,
                mobileAppConfiguration
              )

            override def emailConfiguration: EmailConfiguration =
              EmailConfiguration(
                EmailAddress("test@sc.com"),
                EmailAddress("test@sc.com"),
                skipValidation,
                List(""),
                ".*".r
              )

          }
          components.application
        }

      }

      val loader = new FakeApplicationLoader(skipValidation)

      val app = TestApp.buildApp(loader)

      lazy val reportRepository = loader.components.reportRepository
      lazy val reportFileRepository = loader.components.reportFileRepository
      lazy val eventRepository = loader.components.eventRepository
      lazy val responseConsumerReviewRepository = loader.components.responseConsumerReviewRepository
      lazy val companyRepository = loader.components.companyRepository
      lazy val blacklistedEmailsRepository = loader.components.blacklistedEmailsRepository
    }

  }

}
