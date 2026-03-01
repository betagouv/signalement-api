package controllers

import config._
import controllers.error.AppError.InvalidEmail
import controllers.error.ErrorPayload
import loader.SignalConsoComponents
import models.BlacklistedEmail
import models.report._
import models.report.ReportStatus.PromesseAction
import models.report.delete.ReportAdminAction
import models.report.delete.ReportAdminActionType.ConsumerThreatenByPro
import models.report.delete.ReportAdminActionType.RGPDDeleteRequest
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
import services.S3ServiceInterface
import utils.AuthHelpers._
import utils.Constants.ActionEvent._
import utils.Constants.EventType
import utils.EmailAddress
import utils.Fixtures
import utils.S3ServiceMock
import utils.TestApp

import java.net.URI
import java.time.OffsetDateTime
import java.time.Period
import java.time.temporal.ChronoUnit
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
          ReportFileOrigin.Consumer,
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
            .withAuthCookie(adminIdentity.email, components.cookieAuthenticator)
            .withJsonBody(jsonBody)

        val result = route(app, request).get

        Helpers.status(result) must beEqualTo(NO_CONTENT)
        Helpers.contentAsBytes(result).isEmpty mustEqual true

        val (maybeReport, events) = Await.result(
          for {
            maybeReport <- reportRepository.get(report.id)
            events      <- eventRepository.getEventsWithUsers(List(report.id), EventFilter(None, None))
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
          (RefundBlackMail, REFUND_BLACKMAIL)
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
            ReportFileOrigin.Consumer,
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
              .withAuthCookie(adminIdentity.email, components.cookieAuthenticator)
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

    "delete report" in new Context {

      val testEnv = application()

      import testEnv._

      new WithApplication(app) {

        val company = Fixtures.genCompany.sample.get
        val report  = Fixtures.genReportForCompany(company).sample.get
        val event   = Fixtures.genReponseEventForReport(report.id).sample.get
        val reportFile = ReportFile(
          ReportFileId.generateId(),
          Some(report.id),
          OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
          "fileName",
          "storageName",
          ReportFileOrigin.Consumer,
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
        val jsonBody = Json.toJson(ReportAdminAction(RGPDDeleteRequest, comment = "comment"))

        val request =
          FakeRequest("DELETE", s"/api/reports/${report.id.toString}")
            .withAuthCookie(adminIdentity.email, components.cookieAuthenticator)
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

        maybeReport.map(_.firstName) must beSome("")
        maybeReport.map(_.lastName) must beSome("")
        maybeReport.map(_.lastName) must beSome("")
        maybeReport.flatMap(_.consumerPhone) must beSome("") or beNone
        maybeReport.flatMap(_.consumerReferenceNumber) must beSome("") or beNone
        maybeReport.map(_.email.value) must beSome("")
        maybeReport.map(_.details) must beSome(List.empty[DetailInputValue])
        maybeReport.map(_.status) must beSome(ReportStatus.SuppressionRGPD: ReportStatus)
        maybeReportFile must beNone
        maybeEvent.map(_.details.as[IncomingReportResponse].consumerDetails) must beSome("")
        maybeReview.flatMap(_.details) must beSome("")
        events.headOption.map(_._1.action) shouldEqual Some(RGPD_DELETE_REQUEST)

      }
    }
  }

  trait Context extends Scope {

    def application(skipValidation: Boolean = false) = new {

      val adminIdentity = Fixtures.genAdminUser.sample.get

      class FakeApplicationLoader(skipValidation: Boolean = false) extends ApplicationLoader {
        var components: SignalConsoComponents = _

        override def load(context: ApplicationLoader.Context): Application = {
          components = new SignalConsoComponents(context) {

            override def configuration: Configuration = Configuration(
              "slick.dbs.default.db.connectionPool" -> "disabled",
              "play.mailer.mock"                    -> true,
              "skip-report-email-validation"        -> true,
              "play.tmpDirectory"                   -> "./target"
            ).withFallback(
              super.configuration
            )

            override lazy val s3Service: S3ServiceInterface =
              new S3ServiceMock()
            override def tokenConfiguration =
              TokenConfiguration(None, None, 12.hours, Period.ofDays(60), Period.ZERO, None, Period.ZERO)
            override def uploadConfiguration = UploadConfiguration(Seq.empty)
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
                reportMaxNumberOfAttachments = 20,
                enableRateLimit = false,
                antivirusServiceConfiguration = AntivirusServiceConfiguration("", "", true, false),
                reportsExportLimitMax = 30000,
                reportsExportPdfLimitMax = 1000,
                reportsListLimitMax = 10000,
                disableAuthAttemptRecording = false
              )

            override def emailConfiguration: EmailConfiguration =
              EmailConfiguration(
                EmailAddress("test@sc.com"),
                EmailAddress("test@sc.com"),
                skipValidation,
                List("yopmail.com"),
                ".*".r,
                40,
                extendedComparison = true
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
      lazy val components                       = loader.components

    }

  }

}
