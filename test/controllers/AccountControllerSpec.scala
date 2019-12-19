package controllers

import java.util.UUID

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.{FakeEnvironment, _}
import models._
import net.codingwell.scalaguice.ScalaModule
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import utils.silhouette.auth.AuthEnv
import utils.EmailAddress
import utils.Fixtures


class AccountControllerSpec(implicit ee: ExecutionEnv) extends Specification with Results with Mockito {

  "AccountController" should {

    "changePassword" should {

      "return a BadRequest with errors if passwords are equals" in new Context {
        new WithApplication(application) {

          val jsonBody = Json.obj("newPassword" -> "password", "oldPassword" -> "password")

          val request = FakeRequest(POST, routes.AccountController.changePassword().toString)
            .withAuthenticator[AuthEnv](identLoginInfo)
            .withJsonBody(jsonBody)

          val result = route(application, request).get

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
    }
  }

  trait Context extends Scope {

    val identity = Fixtures.genAdminUser.sample.get
    val identLoginInfo = LoginInfo(CredentialsProvider.ID, identity.email.value)
    implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(identLoginInfo -> identity))

    class FakeModule extends AbstractModule with ScalaModule {
      override def configure() = {
        bind[Environment[AuthEnv]].toInstance(env)
      }
    }

    lazy val application = new GuiceApplicationBuilder()
      .configure(
        Configuration(
          "play.evolutions.enabled" -> false
        )
      )
      .overrides(new FakeModule())
      .build()

  }

}