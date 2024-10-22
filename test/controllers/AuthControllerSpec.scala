package controllers

import authentication.BCryptPasswordHasher
import authentication.PasswordInfo
import controllers.error.AppError._
import controllers.error.ErrorPayload
import controllers.error.ErrorPayload.failedAuthenticationErrorPayload
import loader.SignalConsoComponents
import models._
import models.auth.AuthToken
import models.company.AccessLevel
import models.token.TokenKind.CompanyJoin
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.Application
import play.api.ApplicationLoader
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import utils.AppSpec
import utils.Fixtures
import utils.TestApp
import utils.AuthHelpers._

import java.time.temporal.ChronoUnit
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class AuthControllerSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val validPassword = "testtesttestA1!"

  val identity = Fixtures.genAdminUser.sample.get.copy(
    id = UUID.randomUUID(),
    password = validPassword
  )

  class FakeApplicationLoader extends ApplicationLoader {
    var components: SignalConsoComponents = _

    override def load(context: ApplicationLoader.Context): Application = {
      components = new SignalConsoComponents(context) {}
      components.application
    }

  }

  val loader     = new FakeApplicationLoader()
  val app        = TestApp.buildApp(loader)
  val components = loader.components

  override def afterAll(): Unit = {
    app.stop()
    ()
  }
  lazy val userRepository = components.userRepository

  lazy val passwordHasherRegistry = components.passwordHasherRegistry

  lazy val authAttemptRepository = components.authAttemptRepository
  lazy val companyRepository     = components.companyRepository
  lazy val accessTokenRepository = components.accessTokenRepository
  lazy val authTokenRepository   = components.authTokenRepository

  val proUser = Fixtures.genProUser.sample.get
  val company = Fixtures.genCompany.sample.get

  override def setupData() = Await.result(
    for {
      _ <- userRepository.create(proUser)
      _ <- userRepository.create(identity)
      _ <- companyRepository.getOrCreate(company.siret, company)
      _ <- accessTokenRepository
        .create(AccessToken.build(CompanyJoin, "123456", None, Some(company.id), Some(AccessLevel.ADMIN), None))
    } yield (),
    Duration.Inf
  )

  "AuthController" should {

    "login" should {

      "success on known user " in {

        val login    = identity.email.value
        val jsonBody = Json.obj("login" -> login, "password" -> validPassword)

        val request = FakeRequest(POST, routes.AuthController.authenticate().toString)
          .withJsonBody(jsonBody)

        val result = for {
          res          <- route(app, request).get
          authAttempts <- authAttemptRepository.listAuthAttempts(Some(login), None, None)
        } yield (res, authAttempts)

        Helpers.status(result.map(_._1)) must beEqualTo(OK)

        val authAttempts = Await.result(result.map(_._2), Duration.Inf).entities
        authAttempts.length shouldEqual 1
        authAttempts.headOption.map(_.login) shouldEqual Some(login)
        authAttempts.headOption.flatMap(_.isSuccess) shouldEqual (Some(true))
        authAttempts.headOption.flatMap(_.failureCause) shouldEqual None

      }

      "fail on invalid body" in {

        val jsonBody = Json.obj("newPassword" -> "password", "oldPassword" -> "password")

        val request = FakeRequest(POST, routes.AuthController.authenticate().toString)
          .withJsonBody(jsonBody)

        val result = route(app, request).get

        Helpers.status(result) must beEqualTo(BAD_REQUEST)
        Helpers.contentAsJson(result) must beEqualTo(
          Json.toJson(ErrorPayload(MalformedBody))
        )

      }

      "fail on invalid password " in {

        val login    = proUser.email.value
        val jsonBody = Json.obj("login" -> login, "password" -> "password")

        val request = FakeRequest(POST, routes.AuthController.authenticate().toString)
          .withJsonBody(jsonBody)

        val result = for {
          res          <- route(app, request).get
          authAttempts <- authAttemptRepository.listAuthAttempts(Some(login), None, None)
        } yield (res, authAttempts)

        Helpers.status(result.map(_._1)) must beEqualTo(UNAUTHORIZED)
        Helpers.contentAsJson(result.map(_._1)) must beEqualTo(
          Json.toJson(failedAuthenticationErrorPayload)
        )

        val authAttempts = Await.result(result.map(_._2), Duration.Inf).entities
        authAttempts.length shouldEqual 1
        authAttempts.headOption.map(_.login) shouldEqual Some(login)
        authAttempts.headOption.flatMap(_.isSuccess) shouldEqual (Some(false))
        authAttempts.headOption.flatMap(_.failureCause) shouldEqual (Some(InvalidPassword(login).details))

      }

      "fail on unknown user " in {

        val login    = "login"
        val jsonBody = Json.obj("login" -> "login", "password" -> "password")

        val request = FakeRequest(POST, routes.AuthController.authenticate().toString)
          .withJsonBody(jsonBody)

        val result = for {
          res          <- route(app, request).get
          authAttempts <- authAttemptRepository.listAuthAttempts(Some(login), None, None)
        } yield (res, authAttempts)

        Helpers.status(result.map(_._1)) must beEqualTo(UNAUTHORIZED)
        Helpers.contentAsJson(result.map(_._1)) must beEqualTo(
          Json.toJson(failedAuthenticationErrorPayload)
        )

        val authAttempts = Await.result(result.map(_._2), Duration.Inf).entities
        authAttempts.length shouldEqual 1
        authAttempts.headOption.map(_.login) shouldEqual Some(login)
        authAttempts.headOption.flatMap(_.isSuccess) shouldEqual (Some(false))
        authAttempts.headOption.flatMap(_.failureCause) shouldEqual (Some(UserNotFound(login).details))

      }

    }

  }

  "forgot password" should {

    "fail on invalid body" in {

      val jsonBody = Json.obj("password" -> "password")

      val request = FakeRequest(POST, routes.AuthController.forgotPassword().toString)
        .withJsonBody(jsonBody)

      val result = route(app, request).get

      Helpers.status(result) must beEqualTo(BAD_REQUEST)
      Helpers.contentAsJson(result) must beEqualTo(
        Json.toJson(ErrorPayload(MalformedBody))
      )
    }

    "not create an auth token when user unknown" in {

      val login    = "unknown"
      val jsonBody = Json.obj("login" -> login)

      val request = FakeRequest(POST, routes.AuthController.forgotPassword().toString)
        .withJsonBody(jsonBody)

      val result = for {
        authTokensBefore <- authTokenRepository.list()
        res              <- route(app, request).get
        authTokensAfter  <- authTokenRepository.list()
      } yield (res, authTokensBefore.diff(authTokensAfter))

      Helpers.status(result.map(_._1)) must beEqualTo(OK)
      val authTokenCreated = Await.result(result.map(_._2), Duration.Inf)
      authTokenCreated.length shouldEqual 0

    }

    "successfully create an auth token" in {

      val login    = identity.email.value
      val jsonBody = Json.obj("login" -> login)

      val request = FakeRequest(POST, routes.AuthController.forgotPassword().toString)
        .withJsonBody(jsonBody)

      val result = for {
        res       <- route(app, request).get
        authToken <- authTokenRepository.findForUserId(identity.id)
      } yield (res, authToken)

      Helpers.status(result.map(_._1)) must beEqualTo(OK)
      val authToken = Await.result(result.map(_._2), Duration.Inf)
      authToken.length shouldEqual 1

    }

  }

  "changePassword" should {
    "return a BadRequest with errors if passwords are equals" in {
      val jsonBody = Json.obj("newPassword" -> "password", "oldPassword" -> "password")

      val request = FakeRequest(POST, routes.AuthController.changePassword().toString)
        .withAuthCookie(identity.email, components.cookieAuthenticator)
        .withJsonBody(jsonBody)

      val result = route(app, request).get

      Helpers.status(result) must beEqualTo(BAD_REQUEST)
      Helpers.contentAsJson(result) must beEqualTo(
        Json.toJson(ErrorPayload(SamePasswordError))
      )
    }
  }

  "reset password" should {

    "fail on invalid body" in {

      val jsonBody = Json.obj("login" -> "test")

      val request = FakeRequest(POST, routes.AuthController.resetPassword(UUID.randomUUID()).toString)
        .withJsonBody(jsonBody)

      val result = route(app, request).get

      Helpers.status(result) must beEqualTo(BAD_REQUEST)
      Helpers.contentAsJson(result) must beEqualTo(
        Json.toJson(ErrorPayload(MalformedBody))
      )
    }

    "fail on password not complex enough" in {

      val tokenId = UUID.randomUUID()
      val token =
        AuthToken(tokenId, UUID.randomUUID(), OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).plusMonths(10L))
      val jsonBody = Json.obj("password" -> "newPasswordWithoutSpecialChar123")

      val request = FakeRequest(POST, routes.AuthController.resetPassword(tokenId).toString)
        .withJsonBody(jsonBody)

      val result = for {
        authToken <- authTokenRepository.create(token)
        res       <- route(app, request).get
      } yield (res, authToken)

      Helpers.status(result.map(_._1)) must beEqualTo(BAD_REQUEST)
      Helpers.contentAsJson(result.map(_._1)) must beEqualTo(
        Json.toJson(ErrorPayload(PasswordNotComplexEnoughError))
      )
    }

    "fail on token expired" in {

      val tokenId = UUID.randomUUID()
      val expiredToken =
        AuthToken(tokenId, UUID.randomUUID(), OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusMonths(10L))
      val jsonBody = Json.obj("password" -> "test")

      val request = FakeRequest(POST, routes.AuthController.resetPassword(tokenId).toString)
        .withJsonBody(jsonBody)

      val result = for {
        authToken <- authTokenRepository.create(expiredToken)
        res       <- route(app, request).get
      } yield (res, authToken)

      Helpers.status(result.map(_._1)) must beEqualTo(NOT_FOUND)
      Helpers.contentAsJson(result.map(_._1)) must beEqualTo(
        Json.toJson(ErrorPayload(PasswordTokenNotFoundOrInvalid(tokenId)))
      )
    }

    "fail on token not found" in {

      val tokenId  = UUID.randomUUID()
      val jsonBody = Json.obj("password" -> "test")

      val request = FakeRequest(POST, routes.AuthController.resetPassword(tokenId).toString)
        .withJsonBody(jsonBody)

      val result = route(app, request).get

      Helpers.status(result) must beEqualTo(NOT_FOUND)
      Helpers.contentAsJson(result) must beEqualTo(
        Json.toJson(ErrorPayload(PasswordTokenNotFoundOrInvalid(tokenId)))
      )
    }

    "reset password" in {

      val userId      = UUID.randomUUID()
      val newPassword = "new_pasS_@#3"

      val user = Fixtures.genAdminUser.sample.get
        .copy(
          id = userId,
          password = passwordHasherRegistry.current.hash(validPassword).password
        )
      val tokenId      = UUID.randomUUID()
      val expiredToken = AuthToken(tokenId, user.id, OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).plusMonths(10))
      val jsonBody     = Json.obj("password" -> newPassword)

      val request = FakeRequest(POST, routes.AuthController.resetPassword(tokenId).toString)
        .withJsonBody(jsonBody)

      val result = for {
        _           <- userRepository.create(user)
        _           <- authTokenRepository.create(expiredToken)
        res         <- route(app, request).get
        updatedUser <- userRepository.get(user.id)

      } yield (res, updatedUser)

      Helpers.status(result.map(_._1)) must beEqualTo(NO_CONTENT)
      val updatedUser = Await.result(result.map(_._2), Duration.Inf)
      updatedUser.isDefined shouldEqual true

      updatedUser.map(u =>
        passwordHasherRegistry.current.matches(
          PasswordInfo(BCryptPasswordHasher.ID, password = u.password, salt = Some("SignalConso")),
          newPassword
        )
      ) shouldEqual Some(
        true
      )
    }

  }

}
