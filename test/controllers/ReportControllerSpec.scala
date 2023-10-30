package controllers

import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.FakeEnvironment
import com.mohiva.play.silhouette.test.FakeRequestWithAuthenticator
import config.EmailConfiguration
import config.MobileAppConfiguration
import config.SignalConsoConfiguration
import config.TokenConfiguration
import config.UploadConfiguration
import controllers.error.AppError.InvalidEmail
import controllers.error.ErrorPayload
import loader.SignalConsoComponents
import models.BlacklistedEmail
import models.report.ReportFile
import models.report.ReportFileOrigin
import models.report.ReportStatus.PromesseAction
import models.report.delete.ReportAdminAction
import models.report.delete.ReportAdminActionType.ConsumerThreatenByPro
import models.report.delete.ReportAdminActionType.RGPDRequest
import models.report.delete.ReportAdminActionType.RefundBlackMail
import models.report.delete.ReportAdminActionType.SolvedContractualDispute
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
import repositories.event.EventFilter
import services.MailRetriesService
import services.S3ServiceInterface
import utils.Constants.ActionEvent.CONSUMER_THREATEN_BY_PRO
import utils.Constants.ActionEvent.POST_ACCOUNT_ACTIVATION_DOC
import utils.Constants.ActionEvent.REFUND_BLACKMAIL
import utils.Constants.ActionEvent.RGPD_REQUEST
import utils.Constants.ActionEvent.SOLVED_CONTRACTUAL_DISPUTE
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
        val jsonBody    = Json.toJson(draftReport)

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val result = route(app, request).get

        Helpers.status(result) must beEqualTo(BAD_REQUEST)
        Helpers.contentAsJson(result) must beEqualTo(
          Json.toJson(ErrorPayload(InvalidEmail(draftReport.get.email.value)))
        )
      }
    }

    "block spammed email but return normally" in new Context {
      val blockedEmail = s"${UUID.randomUUID().toString}@gmail.com"
      val testEnv      = application(skipValidation = true)
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
        val jsonBody    = Json.toJson(draftReport)

        val request = FakeRequest("POST", "/api/reports").withJsonBody(jsonBody)

        val result = route(app, request).get
        Helpers.status(result) must beEqualTo(OK)
        (Helpers.contentAsJson(result) \ "id").as[String] must not(beEmpty)
        (Helpers.contentAsJson(result) \ "email").as[String] must beEqualTo(blockedEmail)
      }
    }

    "admin close report" in new Context {

      val testEnv = application()

      import testEnv._

      new WithApplication(app) {

        val company = Fixtures.genCompany.sample.get
        val report  = Fixtures.genReportForCompany(company).sample.get
        val event   = Fixtures.genEventForReport(report.id, EventType.PRO, POST_ACCOUNT_ACTIVATION_DOC).sample.get
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
            _ <- userRepository.create(adminIdentity)
            _ <- companyRepository.create(company)
            _ <- reportRepository.create(report)
            _ <- reportFileRepository.create(reportFile)
            _ <- eventRepository.create(event)
            _ <- responseConsumerReviewRepository.create(review)
          } yield (),
          Duration.Inf
        )

        val jsonBody = Json.toJson(ReportAdminAction(SolvedContractualDispute, comment = "comment"))

        val request =
          FakeRequest("DELETE", s"/api/reports/${report.id.toString}")
            .withAuthenticator[AuthEnv](adminLoginInfo)
            .withJsonBody(jsonBody)

        val result = route(app, request).get

        Helpers.status(result) must beEqualTo(NO_CONTENT)
        Helpers.contentAsBytes(result).isEmpty mustEqual true

        val (maybeReport, events) = Await.result(
          for {
            maybeReport <- reportRepository.get(report.id)
            events      <- eventRepository.getEventsWithUsers(report.id, EventFilter(None, None))
          } yield (maybeReport, events),
          Duration.Inf
        )

        maybeReport.map(_.status.entryName) must beSome(PromesseAction.entryName)
        events.exists(_._1.action == SOLVED_CONTRACTUAL_DISPUTE) shouldEqual true

      }
    }

    "delete report" in new Context {

      forall(
        List(
          (ConsumerThreatenByPro, CONSUMER_THREATEN_BY_PRO),
          (RefundBlackMail, REFUND_BLACKMAIL),
          (RGPDRequest, RGPD_REQUEST)
        )
      ) { case (actionType, expectedActionEvent) =>
        val testEnv = application()

        import testEnv._

        new WithApplication(app) {

          val company = Fixtures.genCompany.sample.get
          val report  = Fixtures.genReportForCompany(company).sample.get
          val event   = Fixtures.genEventForReport(report.id, EventType.PRO, POST_ACCOUNT_ACTIVATION_DOC).sample.get
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
              _ <- userRepository.create(adminIdentity)
              _ <- companyRepository.create(company)
              _ <- reportRepository.create(report)
              _ <- reportFileRepository.create(reportFile)
              _ <- eventRepository.create(event)
              _ <- responseConsumerReviewRepository.create(review)
            } yield (),
            Duration.Inf
          )

          val jsonBody = Json.toJson(ReportAdminAction(actionType, comment = "comment"))

          val request =
            FakeRequest("DELETE", s"/api/reports/${report.id.toString}")
              .withAuthenticator[AuthEnv](adminLoginInfo)
              .withJsonBody(jsonBody)

          val result = route(app, request).get

          Helpers.status(result) must beEqualTo(NO_CONTENT)
          Helpers.contentAsBytes(result).isEmpty mustEqual true

          val (maybeReport, maybeReportFile, maybeEvent, maybeReview, events) = Await.result(
            for {
              maybeReport     <- reportRepository.get(report.id)
              maybeReportFile <- reportFileRepository.get(reportFile.id)
              maybeEvent      <- eventRepository.get(event.id)
              maybeReview     <- responseConsumerReviewRepository.get(review.id)
              events          <- eventRepository.getCompanyEventsWithUsers(company.id, EventFilter(None, None))
            } yield (maybeReport, maybeReportFile, maybeEvent, maybeReview, events),
            Duration.Inf
          )

          maybeReport must beNone
          maybeReportFile must beNone
          maybeEvent must beNone
          maybeReview must beNone
          events.headOption.map(_._1.action) shouldEqual Some(expectedActionEvent)

        }
      }
    }

  }

  trait Context extends Scope {

//    val adminIdentity  = Fixtures.genAdminUser.sample.get
//    val adminLoginInfo = LoginInfo(CredentialsProvider.ID, adminIdentity.email.value)
//    val proIdentity    = Fixtures.genProUser.sample.get
//    val proLoginInfo   = LoginInfo(CredentialsProvider.ID, proIdentity.email.value)
//
//    val companyId = UUID.randomUUID
//
//    implicit val env: Environment[AuthEnv] =
//      new FakeEnvironment[AuthEnv](Seq(adminLoginInfo -> adminIdentity, proLoginInfo -> proIdentity))
//
//    val mailRetriesService = mock[MailRetriesService]
//    val mockS3Service      = new S3ServiceMock()

    def application(skipValidation: Boolean = false) = new {

      val adminIdentity  = Fixtures.genAdminUser.sample.get
      val adminLoginInfo = LoginInfo(CredentialsProvider.ID, adminIdentity.email.value)
      val proIdentity    = Fixtures.genProUser.sample.get
      val proLoginInfo   = LoginInfo(CredentialsProvider.ID, proIdentity.email.value)

      val companyId = UUID.randomUUID

      implicit val env: Environment[AuthEnv] =
        new FakeEnvironment[AuthEnv](Seq(adminLoginInfo -> adminIdentity, proLoginInfo -> proIdentity))

      val mailRetriesService = mock[MailRetriesService]
      val mockS3Service      = new S3ServiceMock()

      class FakeApplicationLoader(skipValidation: Boolean = false) extends ApplicationLoader {
        var components: SignalConsoComponents = _

        override def load(context: ApplicationLoader.Context): Application = {
          components = new SignalConsoComponents(context) {

            override def authEnv: Environment[AuthEnv] = env
            override def configuration: Configuration = Configuration(
              "slick.dbs.default.db.connectionPool" -> "disabled",
              "play.mailer.mock"                    -> true,
              "skip-report-email-validation"        -> true,
              "play.tmpDirectory"                   -> "./target"
            ).withFallback(
              super.configuration
            )

            override def s3Service: S3ServiceInterface = mockS3Service
            override def tokenConfiguration =
              TokenConfiguration(None, None, 12.hours, Period.ofDays(60), Period.ZERO, None)
            override def uploadConfiguration = UploadConfiguration(Seq.empty, false, "/tmp")
            override def mobileAppConfiguration = MobileAppConfiguration(
              minimumAppVersionIos = "1.0.0",
              minimumAppVersionAndroid = "1.0.0"
            )
            override def signalConsoConfiguration: SignalConsoConfiguration =
              SignalConsoConfiguration(
                "",
                new URI("http://test.com"),
                new URI("http://test.com"),
                new URI("http://test.com"),
                tokenConfiguration,
                uploadConfiguration,
                mobileAppConfiguration,
                reportFileMaxSize = 5,
                reportMaxNumberOfAttachments = 20
              )

            override def emailConfiguration: EmailConfiguration =
              EmailConfiguration(
                EmailAddress("test@sc.com"),
                EmailAddress("test@sc.com"),
                skipValidation,
                List(""),
                ".*".r,
                40
              )

          }
          components.application
        }

      }

      val loader = new FakeApplicationLoader(skipValidation)

      val app = TestApp.buildApp(loader)

      lazy val reportRepository                 = loader.components.reportRepository
      lazy val reportFileRepository             = loader.components.reportFileRepository
      lazy val eventRepository                  = loader.components.eventRepository
      lazy val responseConsumerReviewRepository = loader.components.responseConsumerReviewRepository
      lazy val companyRepository                = loader.components.companyRepository
      lazy val userRepository                   = loader.components.userRepository
      lazy val blacklistedEmailsRepository      = loader.components.blacklistedEmailsRepository

    }

  }

}
