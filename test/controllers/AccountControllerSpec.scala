package controllers

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.{FakeEnvironment, _}
import models._
import repositories._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.matcher.FutureMatchers
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import utils.silhouette.auth.AuthEnv
import utils.AppSpec
import utils.EmailAddress
import utils.Fixtures


class AccountControllerSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with Results with FutureMatchers {

  val identity = Fixtures.genAdminUser.sample.get
  val identLoginInfo = LoginInfo(CredentialsProvider.ID, identity.email.value)
  implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(identLoginInfo -> identity))

  lazy val userRepository = app.injector.instanceOf[UserRepository]
  lazy val companyRepository = app.injector.instanceOf[CompanyRepository]
  lazy val companyAccessRepository = app.injector.instanceOf[CompanyAccessRepository]

  override def configureFakeModule(): AbstractModule = {
    new FakeModule
  }

  class FakeModule extends AppFakeModule {
    override def configure() = {
      super.configure
      bind[Environment[AuthEnv]].toInstance(env)
    }
  }

  val proUser = Fixtures.genProUser.sample.get
  val company = Fixtures.genCompany.sample.get
  override def setupData = {
    Await.result(for {
      _ <- userRepository.create(proUser)
      _ <- companyRepository.getOrCreate(company.siret, company)
      _ <- companyAccessRepository.createToken(company, AccessLevel.ADMIN, "123456", None, None)
    } yield Unit,
    Duration.Inf)
  }

  "AccountController" should {
    "changePassword" should {
      "return a BadRequest with errors if passwords are equals" in {
          val jsonBody = Json.obj("newPassword" -> "password", "oldPassword" -> "password")

          val request = FakeRequest(POST, routes.AccountController.changePassword().toString)
            .withAuthenticator[AuthEnv](identLoginInfo)
            .withJsonBody(jsonBody)

          val result = route(app, request).get

          Helpers.status(result) must beEqualTo(BAD_REQUEST)
          Helpers.contentAsJson(result) must beEqualTo(
            Json.obj(
              "obj" -> Seq(
                Json.obj("msg" -> Seq("Passwords must not be equals"), "args" -> Json.toJson(Seq.empty))
              )
            )
          )
      }
    }

    "activateAccount" should {
      "raise a 409 in case of duplicate email addresse" in {
        val request = FakeRequest(POST, routes.AccountController.activateAccount.toString)
          .withJsonBody(Json.obj(
            "draftUser" -> Json.obj(
              "email" -> proUser.email,
              "firstName" -> proUser.firstName,
              "lastName" -> proUser.lastName,
              "password" -> proUser.password
            ),
            "tokenInfo" -> Json.obj(
              "token" -> "123456",
              "companySiret" -> company.siret
            )
          ))

        val result = route(app, request).get

        Helpers.status(result) must beEqualTo(409)
      }

      "use preexisting tokens with same email, if any" in {
        val newUser = Fixtures.genUser.sample.get
        val otherCompany = Fixtures.genCompany.sample.get
        val otherToken = Await.result(for {
          _ <- companyRepository.getOrCreate(otherCompany.siret, otherCompany)
          _ <- companyAccessRepository.createToken(company, AccessLevel.ADMIN, "000000", None, Some(newUser.email))
          token <- companyAccessRepository.createToken(otherCompany, AccessLevel.ADMIN, "whatever", None, Some(newUser.email))
        } yield token,
        Duration.Inf)
        val request = FakeRequest(POST, routes.AccountController.activateAccount.toString)
          .withJsonBody(Json.obj(
            "draftUser" -> Json.obj(
              "email" -> newUser.email,
              "firstName" -> newUser.firstName,
              "lastName" -> newUser.lastName,
              "password" -> newUser.password
            ),
            "tokenInfo" -> Json.obj(
              "token" -> "000000",
              "companySiret" -> company.siret
            )
          ))

        val result = route(app, request).get
        Helpers.status(result) must beEqualTo(204)

        companyAccessRepository.fetchAdmins(company).map(_.length) must beEqualTo(1).await
        companyAccessRepository.fetchAdmins(otherCompany).map(_.length) must beEqualTo(1).await
      }
    }
  }
}
