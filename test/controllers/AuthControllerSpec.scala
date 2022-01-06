package controllers

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.test.FakeEnvironment
import controllers.error.ErrorPayload
import controllers.error.AppError.InvalidPassword
import controllers.error.AppError.MalformedBody
import controllers.error.AppError.TokenNotFoundOrInvalid
import controllers.error.AppError.UserNotFound
import controllers.error.ErrorPayload.AuthenticationErrorPayload
import models._
import models.auth.AuthToken
import models.token.TokenKind.CompanyJoin
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import repositories._
import utils.AppSpec
import utils.Fixtures
import utils.silhouette.auth.AuthEnv

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class AuthControllerSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val validPassword = "test"
  val identity = Fixtures.genAdminUser.sample.get
    .copy(password =
      PasswordInfo(BCryptPasswordHasher.ID, password = validPassword, salt = Some("SignalConso")).password
    )
  val identLoginInfo = LoginInfo(CredentialsProvider.ID, identity.email.value)
  implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(identLoginInfo -> identity))

  lazy val userRepository = app.injector.instanceOf[UserRepository]
  lazy val passwordHasherRegistry = app.injector.instanceOf[PasswordHasherRegistry]
  lazy val companyRepository = app.injector.instanceOf[CompanyRepository]
  lazy val accessTokenRepository = app.injector.instanceOf[AccessTokenRepository]
  lazy val authTokenRepository = app.injector.instanceOf[AuthTokenRepository]

  override def configureFakeModule(): AbstractModule =
    new FakeModule

  class FakeModule extends AppFakeModule {
    override def configure() = {
      super.configure
      bind[Environment[AuthEnv]].toInstance(env)
    }
  }

  val proUser = Fixtures.genProUser.sample.get
  val company = Fixtures.genCompany.sample.get
  override def setupData() =
    Await.result(
      for {
        _ <- userRepository.create(proUser)
        _ <- userRepository.create(identity)
        _ <- companyRepository.getOrCreate(company.siret, company)
        _ <- accessTokenRepository
          .createToken(CompanyJoin, "123456", None, Some(company.id), Some(AccessLevel.ADMIN), None)
      } yield (),
      Duration.Inf
    )

  "AccountController" should {
    "login" should {
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

        val login = proUser.email.value
        val jsonBody = Json.obj("login" -> login, "password" -> "password")

        val request = FakeRequest(POST, routes.AuthController.authenticate().toString)
          .withJsonBody(jsonBody)

        val result = for {
          res <- route(app, request).get
          authAttempts <- userRepository.listAuthAttempts(login)
        } yield (res, authAttempts)

        Helpers.status(result.map(_._1)) must beEqualTo(UNAUTHORIZED)
        Helpers.contentAsJson(result.map(_._1)) must beEqualTo(
          Json.toJson(AuthenticationErrorPayload)
        )

        val authAttempts = Await.result(result.map(_._2), Duration.Inf)
        authAttempts.length shouldEqual 1
        authAttempts.headOption.map(_.login) shouldEqual Some(login)
        authAttempts.headOption.flatMap(_.isSuccess) shouldEqual (Some(false))
        authAttempts.headOption.flatMap(_.failureCause) shouldEqual (Some(InvalidPassword(login).details))

      }

      "fail on unknown user " in {

        val login = "login"
        val jsonBody = Json.obj("login" -> "login", "password" -> "password")

        val request = FakeRequest(POST, routes.AuthController.authenticate().toString)
          .withJsonBody(jsonBody)

        val result = for {
          res <- route(app, request).get
          authAttempts <- userRepository.listAuthAttempts(login)
        } yield (res, authAttempts)

        Helpers.status(result.map(_._1)) must beEqualTo(UNAUTHORIZED)
        Helpers.contentAsJson(result.map(_._1)) must beEqualTo(
          Json.toJson(AuthenticationErrorPayload)
        )

        val authAttempts = Await.result(result.map(_._2), Duration.Inf)
        authAttempts.length shouldEqual 1
        authAttempts.headOption.map(_.login) shouldEqual Some(login)
        authAttempts.headOption.flatMap(_.isSuccess) shouldEqual (Some(false))
        authAttempts.headOption.flatMap(_.failureCause) shouldEqual (Some(UserNotFound(login).details))

      }

    }

    "success on known user " in {

      val login = identity.email.value
      val jsonBody = Json.obj("login" -> login, "password" -> validPassword)

      val request = FakeRequest(POST, routes.AuthController.authenticate().toString)
        .withJsonBody(jsonBody)

      val result = for {
        res <- route(app, request).get
        authAttempts <- userRepository.listAuthAttempts(login)
      } yield (res, authAttempts)

      Helpers.status(result.map(_._1)) must beEqualTo(OK)

      val authAttempts = Await.result(result.map(_._2), Duration.Inf)
      authAttempts.length shouldEqual 1
      authAttempts.headOption.map(_.login) shouldEqual Some(login)
      authAttempts.headOption.flatMap(_.isSuccess) shouldEqual (Some(true))
      authAttempts.headOption.flatMap(_.failureCause) shouldEqual None

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

      val login = "unknown"
      val jsonBody = Json.obj("login" -> login)

      val request = FakeRequest(POST, routes.AuthController.forgotPassword().toString)
        .withJsonBody(jsonBody)

      val result = for {
        authTokensBefore <- authTokenRepository.list()
        res <- route(app, request).get
        authTokensAfter <- authTokenRepository.list()
      } yield (res, authTokensBefore.diff(authTokensAfter))

      Helpers.status(result.map(_._1)) must beEqualTo(OK)
      val authTokenCreated = Await.result(result.map(_._2), Duration.Inf)
      authTokenCreated.length shouldEqual 0

    }

    "successfully create an auth token" in {

      val login = identity.email.value
      val jsonBody = Json.obj("login" -> login)

      val request = FakeRequest(POST, routes.AuthController.forgotPassword().toString)
        .withJsonBody(jsonBody)

      val result = for {
        res <- route(app, request).get
        authToken <- authTokenRepository.findForUserId(identity.id)
      } yield (res, authToken)

      Helpers.status(result.map(_._1)) must beEqualTo(OK)
      val authToken = Await.result(result.map(_._2), Duration.Inf)
      authToken.length shouldEqual 1

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

    "fail on token expired" in {

      val tokenId = UUID.randomUUID()
      val expiredToken = AuthToken(tokenId, UUID.randomUUID(), OffsetDateTime.now().minusMonths(10))
      val jsonBody = Json.obj("password" -> "test")

      val request = FakeRequest(POST, routes.AuthController.resetPassword(tokenId).toString)
        .withJsonBody(jsonBody)

      val result = for {
        authToken <- authTokenRepository.create(expiredToken)
        res <- route(app, request).get
      } yield (res, authToken)

      Helpers.status(result.map(_._1)) must beEqualTo(NOT_FOUND)
      Helpers.contentAsJson(result.map(_._1)) must beEqualTo(
        Json.toJson(ErrorPayload(TokenNotFoundOrInvalid(tokenId)))
      )
    }

    "fail on token not found" in {

      val tokenId = UUID.randomUUID()
      val jsonBody = Json.obj("password" -> "test")

      val request = FakeRequest(POST, routes.AuthController.resetPassword(tokenId).toString)
        .withJsonBody(jsonBody)

      val result = route(app, request).get

      Helpers.status(result) must beEqualTo(NOT_FOUND)
      Helpers.contentAsJson(result) must beEqualTo(
        Json.toJson(ErrorPayload(TokenNotFoundOrInvalid(tokenId)))
      )
    }

  }

}
