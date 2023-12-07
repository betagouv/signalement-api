package controllers

import models.UserReportsFilters
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.AppSpec
import utils.Fixtures
import utils.TestApp
import utils.AuthHelpers._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class UserReportsFiltersControllerSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val user  = Fixtures.genDgccrfUser.sample.get
  val user2 = Fixtures.genDgccrfUser.sample.get

  val (app, components) = TestApp.buildApp(
  )

  override def afterAll(): Unit = {
    app.stop()
    ()
  }

  lazy val userRepository               = components.userRepository
  lazy val userReportsFiltersRepository = components.userReportsFiltersRepository

  override def setupData() =
    Await.result(
      for {
        _ <- userRepository.create(user)
        _ <- userRepository.create(user2)
      } yield (),
      Duration.Inf
    )

  "UserReportsFiltersController" should {
    sequential
    "list only filters of the user" in {
      val request = FakeRequest(GET, routes.UserReportsFiltersController.list().toString)
        .withAuthCookie(user.email, components.cookieAuthenticator)

      val result = for {
        _   <- userReportsFiltersRepository.createOrUpdate(UserReportsFilters(user.id, "test_list", Json.obj()))
        _   <- userReportsFiltersRepository.createOrUpdate(UserReportsFilters(user2.id, "test_list", Json.obj()))
        res <- route(app, request).get
      } yield res

      status(result) must beEqualTo(OK)
      contentAsJson(result).as[List[JsValue]] must haveLength(1)
    }
    "save minimal reports filters" in {
      val jsonBody = Json.obj(
        "name"    -> "test",
        "filters" -> Json.obj()
      )

      val request = FakeRequest(POST, routes.UserReportsFiltersController.save().toString)
        .withJsonBody(jsonBody)
        .withAuthCookie(user.email, components.cookieAuthenticator)

      val result = for {
        res          <- route(app, request).get
        savedFilters <- userReportsFiltersRepository.get(user.id, "test")
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
      val jsonBody = Json.obj(
        "name"    -> "test",
        "filters" -> Json.parse(jsonString)
      )

      val request = FakeRequest(POST, routes.UserReportsFiltersController.save().toString)
        .withJsonBody(jsonBody)
        .withAuthCookie(user.email, components.cookieAuthenticator)

      val result = for {
        res          <- route(app, request).get
        savedFilters <- userReportsFiltersRepository.get(user.id, "test")
      } yield (res, savedFilters.get.reportsFilters)

      status(result.map(_._1)) must beEqualTo(NO_CONTENT)
      result.map(_._2) must beEqualTo(Json.parse(jsonString)).await
    }

    "reject when user is not authenticated" in {
      val jsonBody = Json.obj(
        "name"    -> "",
        "filters" -> Json.obj("test" -> "test")
      )

      val request = FakeRequest(POST, routes.UserReportsFiltersController.save().toString)
        .withJsonBody(jsonBody)

      val result = for {
        res <- route(app, request).get
      } yield res

      status(result) must beEqualTo(UNAUTHORIZED)

    }
  }
}
