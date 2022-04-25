package controllers

import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.test.FakeEnvironment
import config.EmailConfiguration
import controllers.error.AppError.EmailOrCodeIncorrect
import controllers.error.AppError.InvalidEmail
import controllers.error.AppError.InvalidEmailProvider
import controllers.error.ErrorPayload
import models.EmailValidationCreate
import models.email.EmailValidationResult
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import repositories.emailvalidation.EmailValidationRepository
import services.Email.ConsumerValidateEmail

import java.time.OffsetDateTime
import scala.concurrent.Await
import services.AttachementService
import services.MailerService
import utils.AppSpec
import utils.EmailAddress
import utils.Fixtures
import utils.FrontRoute
import utils.EmailAddress.EmptyEmailAddress
import utils.silhouette.auth.AuthEnv

import scala.concurrent.duration.Duration

class EmailValidationControllerSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq.empty)
  lazy val emailValidationRepository: EmailValidationRepository =
    injector.instanceOf[EmailValidationRepository]

  lazy val mailerService = injector.instanceOf[MailerService]
  lazy val attachementService = injector.instanceOf[AttachementService]

  lazy val frontRoute = injector.instanceOf[FrontRoute]
  lazy val contactAddress = emailConfiguration.contactAddress

  class FakeModule(skipValidation: Boolean = false, emailProviderBlocklist: List[String]) extends AppFakeModule {
    override def configure() = {
      super.configure
      bind[Environment[AuthEnv]].toInstance(env)
      bind[EmailConfiguration].toInstance(
        EmailConfiguration(
          from = EmptyEmailAddress,
          contactAddress = EmptyEmailAddress,
          skipReportEmailValidation = skipValidation,
          ccrfEmailSuffix = "*",
          emailProvidersBlocklist = emailProviderBlocklist
        )
      )
    }
  }

  def app(skipValidation: Boolean = false, emailProviderBlocklist: List[String] = List.empty) =
    new GuiceApplicationBuilder()
      .overrides(new FakeModule(skipValidation, emailProviderBlocklist))
      .build()

  val proUser = Fixtures.genProUser.sample.get
  val company = Fixtures.genCompany.sample.get

  "EmailValidationController" should {

    "check email with provider blocklist configured " should {

      "validate email successfully " in {
        new WithApplication(app(skipValidation = true, emailProviderBlocklist = List("yopmail.com", "trash.com"))) {

          val request = FakeRequest(POST, routes.EmailValidationController.checkEmail().toString)
            .withJsonBody(Json.obj("email" -> "user@dgccrf.gouv.fr"))
          val result = route(app, request).get
          Helpers.status(result) must beEqualTo(200)
          Helpers.contentAsJson(result) must beEqualTo(
            Json.toJson(EmailValidationResult.success)
          )
        }
      }

      "not validate blocked provider email" in {
        new WithApplication(app(skipValidation = true, emailProviderBlocklist = List("yopmail.com", "trash.com"))) {
          val request = FakeRequest(POST, routes.EmailValidationController.checkEmail().toString)
            .withJsonBody(Json.obj("email" -> "user@yopmail.com"))
          val result = route(app, request).get
          Helpers.status(result) must beEqualTo(400)
          Helpers.contentAsJson(result) must beEqualTo(
            Json.toJson(ErrorPayload(InvalidEmailProvider))
          )
        }

      }

    }

    "check email when skipping validation flag is ON" should {

      "validate email successfully " in {
        new WithApplication(app(skipValidation = true)) {

          val request = FakeRequest(POST, routes.EmailValidationController.checkEmail().toString)
            .withJsonBody(Json.obj("email" -> "user@dgccrf.gouv.fr"))
          val result = route(app, request).get
          Helpers.status(result) must beEqualTo(200)
          Helpers.contentAsJson(result) must beEqualTo(
            Json.toJson(EmailValidationResult.success)
          )

        }
      }

      "not validate malformed email" in {
        new WithApplication(app(skipValidation = true)) {
          val malformedEmail = "user@dgccrf"
          val request = FakeRequest(POST, routes.EmailValidationController.checkEmail().toString)
            .withJsonBody(Json.obj("email" -> malformedEmail))
          val result = route(app, request).get
          Helpers.status(result) must beEqualTo(400)
          Helpers.contentAsJson(result) must beEqualTo(
            Json.toJson(ErrorPayload(InvalidEmail(malformedEmail)))
          )
        }

      }

    }

    "check email when skipping validation flag is OFF" should {

      "create email validation entry and send email when email is new" in {
        val unknownEmail = Fixtures.genEmailAddress.sample.get

        val request = FakeRequest(POST, routes.EmailValidationController.checkEmail().toString)
          .withJsonBody(Json.obj("email" -> unknownEmail.value))

        val result = for {
          res <- route(app, request).get
          emailValidation <- emailValidationRepository.findByEmail(unknownEmail)
        } yield (res, emailValidation)

        Helpers.status(result.map(_._1)) must beEqualTo(200)
        Helpers.contentAsJson(result.map(_._1)) must beEqualTo(
          Json.toJson(EmailValidationResult.failure)
        )
        val maybeEmailValidation = Await.result(result.map(_._2), Duration.Inf)
        maybeEmailValidation.isDefined shouldEqual true
        maybeEmailValidation.flatMap(_.lastValidationDate) shouldEqual None
        val expectedEmail = maybeEmailValidation.map(emailValidation => ConsumerValidateEmail(emailValidation))
        val emailSubject = expectedEmail.map(_.subject).get
        val emailBody = expectedEmail.map(_.getBody(frontRoute, contactAddress)).get
        mailMustHaveBeenSent(Seq(unknownEmail), emailSubject, emailBody)
      }

      "not validate email when email exist but not validated" in {
        val existingEmail: EmailAddress = Fixtures.genEmailAddress.sample.get
        val emailValidation = EmailValidationCreate(email = existingEmail)

        val request = FakeRequest(POST, routes.EmailValidationController.checkEmail().toString)
          .withJsonBody(Json.obj("email" -> existingEmail.value))

        val result = for {
          _ <- emailValidationRepository.create(emailValidation)
          res <- route(app, request).get
          emailValidation <- emailValidationRepository.findByEmail(existingEmail)
        } yield (res, emailValidation)

        Helpers.status(result.map(_._1)) must beEqualTo(200)
        Helpers.contentAsJson(result.map(_._1)) must beEqualTo(
          Json.toJson(EmailValidationResult.failure)
        )
        val maybeEmailValidation = Await.result(result.map(_._2), Duration.Inf)
        maybeEmailValidation.isDefined shouldEqual true
        maybeEmailValidation.flatMap(_.lastValidationDate) shouldEqual None
        val expectedEmail = maybeEmailValidation.map(emailValidation => ConsumerValidateEmail(emailValidation))
        val emailSubject = expectedEmail.map(_.subject).get
        val emailBody = expectedEmail.map(_.getBody(frontRoute, contactAddress)).get
        mailMustHaveBeenSent(Seq(existingEmail), emailSubject, emailBody)
      }

      "valid email if it has been already validated" in {
        val existingEmail = Fixtures.genEmailAddress.sample.get
        val request = FakeRequest(POST, routes.EmailValidationController.checkEmail().toString)
          .withJsonBody(Json.obj("email" -> existingEmail.value))

        val result = for {
          _ <- emailValidationRepository.create(
            EmailValidationCreate(email = existingEmail).copy(lastValidationDate = Some(OffsetDateTime.now))
          )
          res <- route(app, request).get
        } yield res
        Helpers.status(result) must beEqualTo(200)
        Helpers.contentAsJson(result) must beEqualTo(
          Json.toJson(EmailValidationResult.success)
        )
      }
    }

    "validateEmail" should {

      "validate email successfully" in {

        val email = Fixtures.genEmailAddress.sample.get

        val result = for {
          emailValidation <- emailValidationRepository.create(
            EmailValidationCreate(email = email)
          )
          request = FakeRequest(POST, routes.EmailValidationController.validEmail().toString)
            .withJsonBody(
              Json.obj("email" -> emailValidation.email.value, "confirmationCode" -> emailValidation.confirmationCode)
            )
          res <- route(app, request).get
          emailValidation <- emailValidationRepository.findByEmail(email)
        } yield (res, emailValidation)

        Helpers.status(result.map(_._1)) must beEqualTo(200)
        Helpers.contentAsJson(result.map(_._1)) must beEqualTo(
          Json.toJson(EmailValidationResult.success)
        )
        val maybeEmailValidation = Await.result(result.map(_._2), Duration.Inf)
        maybeEmailValidation.isDefined shouldEqual true
        maybeEmailValidation.flatMap(_.lastValidationDate).isDefined shouldEqual true

      }

      "fail when email not found" in {

        val email = Fixtures.genEmailAddress.sample.get

        val request = FakeRequest(POST, routes.EmailValidationController.validEmail().toString)
          .withJsonBody(
            Json.obj("email" -> email.value, "confirmationCode" -> "111111")
          )

        val result = for {
          res <- route(app, request).get
          emailValidation <- emailValidationRepository.findByEmail(email)
        } yield (res, emailValidation)

        Helpers.status(result.map(_._1)) must beEqualTo(404)
        Helpers.contentAsJson(result.map(_._1)) must beEqualTo(
          Json.toJson(ErrorPayload(EmailOrCodeIncorrect(email)))
        )
        val maybeEmailValidation = Await.result(result.map(_._2), Duration.Inf)
        maybeEmailValidation.isDefined shouldEqual false

      }

      "fail when code is incorrect" in {

        val email = Fixtures.genEmailAddress.sample.get

        val result = for {
          emailValidation <- emailValidationRepository.create(
            EmailValidationCreate(email = email)
          )
          request = FakeRequest(POST, routes.EmailValidationController.validEmail().toString)
            .withJsonBody(
              Json.obj("email" -> emailValidation.email.value, "confirmationCode" -> "XXXXXX")
            )
          res <- route(app, request).get
          emailValidation <- emailValidationRepository.findByEmail(email)
        } yield (res, emailValidation)

        Helpers.status(result.map(_._1)) must beEqualTo(200)
        Helpers.contentAsJson(result.map(_._1)) must beEqualTo(
          Json.toJson(EmailValidationResult.invalidCode)
        )
        val maybeEmailValidation = Await.result(result.map(_._2), Duration.Inf)
        maybeEmailValidation.isDefined shouldEqual true
        maybeEmailValidation.flatMap(_.lastValidationDate).isDefined shouldEqual false

      }

    }

  }
  def mailMustHaveBeenSent(recipients: Seq[EmailAddress], subject: String, bodyHtml: String) =
    there was one(mailerService)
      .sendEmail(
        emailConfiguration.from,
        recipients,
        Nil,
        subject,
        bodyHtml,
        attachementService.defaultAttachments
      )

}
