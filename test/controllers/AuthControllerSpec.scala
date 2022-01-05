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
import controllers.error.AppError.MalformedBody
import controllers.error.ErrorPayload.AuthenticationErrorPayload
import models._
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

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class AuthControllerSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val identity = Fixtures.genAdminUser.sample.get
    .copy(password = PasswordInfo(BCryptPasswordHasher.ID, password = "test", salt = Some("SignalConso")).password)
  val identLoginInfo = LoginInfo(CredentialsProvider.ID, identity.email.value)
  implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(identLoginInfo -> identity))

  lazy val userRepository = app.injector.instanceOf[UserRepository]
  lazy val passwordHasherRegistry = app.injector.instanceOf[PasswordHasherRegistry]
  lazy val companyRepository = app.injector.instanceOf[CompanyRepository]
  lazy val accessTokenRepository = app.injector.instanceOf[AccessTokenRepository]

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
          //            .withAuthenticator[AuthEnv](identLoginInfo)
          .withJsonBody(jsonBody)

        val result = route(app, request).get

        Helpers.status(result) must beEqualTo(BAD_REQUEST)
        Helpers.contentAsJson(result) must beEqualTo(
          Json.toJson(ErrorPayload(MalformedBody))
        )
      }

      "fail on invalid password " in {

        val jsonBody = Json.obj("login" -> proUser.email, "password" -> "password")

        val request = FakeRequest(POST, routes.AuthController.authenticate().toString)
          .withJsonBody(jsonBody)

        val result = route(app, request).get

        Helpers.status(result) must beEqualTo(UNAUTHORIZED)
        Helpers.contentAsJson(result) must beEqualTo(
          Json.toJson(AuthenticationErrorPayload)
        )
      }

      "fail on unknown user " in {

        val jsonBody = Json.obj("login" -> "login", "password" -> "password")

        val request = FakeRequest(POST, routes.AuthController.authenticate().toString)
          .withJsonBody(jsonBody)

        val result = route(app, request).get

        Helpers.status(result) must beEqualTo(UNAUTHORIZED)
        Helpers.contentAsJson(result) must beEqualTo(
          Json.toJson(AuthenticationErrorPayload)
        )
      }

    }

    "success on known user " in {

      val jsonBody = Json.obj("login" -> identity.email, "password" -> "test")

      val request = FakeRequest(POST, routes.AuthController.authenticate().toString)
        .withJsonBody(jsonBody)

      val result = route(app, request).get

      Helpers.status(result) must beEqualTo(UNAUTHORIZED)
      Helpers.contentAsJson(result) must beEqualTo(
        Json.toJson(AuthenticationErrorPayload)
      )
    }

  }

//
//      "send a DGCCRF invitation" in {
//        val request = FakeRequest(POST, routes.AccountController.sendDGCCRFInvitation().toString)
//          .withAuthenticator[AuthEnv](identLoginInfo)
//          .withJsonBody(Json.obj("email" -> "user@dgccrf.gouv.fr"))
//
//        val result = route(app, request).get
//        Helpers.status(result) must beEqualTo(200)
//      }

}
