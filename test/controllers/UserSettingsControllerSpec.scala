package controllers

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.FakeEnvironment
import com.mohiva.play.silhouette.test.FakeRequestWithAuthenticator
import models.User
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.silhouette.auth.AuthEnv
import utils.AppSpec
import utils.Fixtures
import utils.TestApp

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class UserSettingsControllerSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val user = Fixtures.genProUser.sample.get

  def loginInfo(user: User) = LoginInfo(CredentialsProvider.ID, user.email.value)

  val (app, components) = TestApp.buildApp(
    Some(
      new FakeEnvironment[AuthEnv](Seq(user).map(user => loginInfo(user) -> user))
    )
  )

  override def afterAll(): Unit = {
    app.stop()
    ()
  }

  implicit val authEnv = components.authEnv
  lazy val userRepository = components.userRepository
  lazy val userSettingsRepository = components.userSettingsRepository

  override def setupData() =
    Await.result(
      for {
        _ <- userRepository.create(user)
      } yield (),
      Duration.Inf
    )

  "UserSettingsController" should {
    sequential
    "save minimal reports filters" in {
      val jsonBody = Json.obj()

      val request = FakeRequest(POST, routes.UserSettingsController.saveReportsFilters().toString)
        .withJsonBody(jsonBody)
        .withAuthenticator[AuthEnv](loginInfo(user))

      val result = for {
        res <- route(app, request).get
        savedFilters <- userSettingsRepository.get(user.id)
      } yield (res, savedFilters.get.reportsFilters)

      status(result.map(_._1)) must beEqualTo(NO_CONTENT)
      result.map(_._2) must beEqualTo(Json.obj()).await

    }

    "save full reports filters" in {
      val jsonString =
        """
          |{
          |  "withTags": [
          |    "DemarchageTelephonique",
          |    "DemarchageInternet"
          |  ],
          |  "withoutTags": [
          |    "AbsenceDeMediateur"
          |  ],
          |  "companyCountries": [
          |    "CY",
          |    "HR"
          |  ],
          |  "siretSirenList": [
          |    "12345"
          |  ],
          |  "activityCodes": [
          |    "01.12Z",
          |    "01.13Z"
          |  ],
          |  "status": [
          |    "TraitementEnCours",
          |    "Transmis"
          |  ],
          |  "email": "c",
          |  "websiteURL": "a",
          |  "phone": "b",
          |  "category": "Eau / Gaz / Electricité",
          |  "details": "test",
          |  "contactAgreement": false,
          |  "hasPhone": true,
          |  "hasWebsite": true,
          |  "hasForeignCountry": true,
          |  "hasCompany": true,
          |  "hasAttachment": true
          |}
          |""".stripMargin
      val jsonBody = Json.parse(jsonString)

      val request = FakeRequest(POST, routes.UserSettingsController.saveReportsFilters().toString)
        .withJsonBody(jsonBody)
        .withAuthenticator[AuthEnv](loginInfo(user))

      val result = for {
        res <- route(app, request).get
        savedFilters <- userSettingsRepository.get(user.id)
      } yield (res, savedFilters.get.reportsFilters)

      status(result.map(_._1)) must beEqualTo(NO_CONTENT)
      result.map(_._2) must beEqualTo(jsonBody).await
    }

    "reject when user is not authenticated" in {
      val jsonBody = Json.obj("test" -> "test")

      val request = FakeRequest(POST, routes.UserSettingsController.saveReportsFilters().toString)
        .withJsonBody(jsonBody)

      val result = for {
        res <- route(app, request).get
      } yield res

      status(result) must beEqualTo(UNAUTHORIZED)

    }
  }
}
