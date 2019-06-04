package controllers

import java.util.UUID

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import com.mohiva.play.silhouette.api.{Environment, LoginInfo, Silhouette}
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
import play.api.libs.json.{JsObject, JsString, Json, JsonValidationError}
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import repositories.UserRepository
import utils.silhouette.{AuthEnv, UserService}

class AuthControllerSpec(implicit ee: ExecutionEnv) extends Specification with Results with Mockito {

  "AuthController" should {

    "changePassword" should {

      "return a BadRequest with errors if passwords are equals" in new Context {
        new WithApplication(application) {

          val controller = new AuthController(mock[Silhouette[AuthEnv]], mock[UserService], mock[UserRepository], mock[CredentialsProvider], mock[PasswordHasherRegistry]) {
            override def controllerComponents: ControllerComponents = Helpers.stubControllerComponents()
          }

          val jsonBody = Json.obj("newPassword" -> "password", "oldPassword" -> "password")

          val request = FakeRequest(POST, routes.AuthController.changePassword().toString)
            .withAuthenticator[AuthEnv](identLoginInfo)
            .withJsonBody(jsonBody)


          val result = route(application, request).get

          Helpers.status(result) must beEqualTo(BAD_REQUEST)
          contentAsJson(result) must beEqualTo(
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

    val identity = User(UUID.randomUUID(),"test@signalconso.beta.gouv.fr", "password", None, Some("Prénom"), Some("Nom"), Some("email"), UserRoles.Admin)
    val identLoginInfo = LoginInfo(CredentialsProvider.ID, identity.login)
    implicit val env: Environment[AuthEnv] = new FakeEnvironment[AuthEnv](Seq(identLoginInfo -> identity))

    class FakeModule extends AbstractModule with ScalaModule {
      override def configure() = {
        bind[Environment[AuthEnv]].toInstance(env)
      }
    }

    lazy val application = new GuiceApplicationBuilder()
      .configure(Configuration("play.evolutions.enabled" -> false))
      .overrides(new FakeModule())
      .build()

  }

}