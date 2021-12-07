package controllers

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.FakeEnvironment
import models.PaginatedResult
import net.codingwell.scalaguice.ScalaModule
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Configuration
import play.api.Logger
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import repositories.ReportRepository
import utils.Fixtures
import utils.silhouette.auth.AuthEnv

import java.util.UUID
import scala.concurrent.Future

class ReportToExternalControllerSpec(implicit ee: ExecutionEnv) extends Specification with Results with Mockito {

  val logger: Logger = Logger(this.getClass)

  "getReportCountBySiret" should {
    val siretFixture = Fixtures.genSiret().sample.get

    "return unauthorized when there no X-Api-Key header" should {

      "ReportController1" in new Context {
        new WithApplication(application) {
          val request = FakeRequest("GET", s"/api/ext/reports/siret/$siretFixture")
          val result = route(application, request).get
          Helpers.status(result) must beEqualTo(UNAUTHORIZED)
        }
      }
    }

    "return unauthorized when X-Api-Key header is invalid" should {

      "ReportController2" in new Context {
        new WithApplication(application) {
          val request = FakeRequest("GET", s"/api/ext/reports/siret/$siretFixture").withHeaders(
            "X-Api-Key" -> "invalid_key"
          )
          val result = route(application, request).get
          Helpers.status(result) must beEqualTo(UNAUTHORIZED)
        }
      }
    }

    "return report count when X-Api-Key header is valid" should {

      "ReportController3" in new Context {
        new WithApplication(application) {
          val request = FakeRequest("GET", s"/api/ext/reports/siret/$siretFixture").withHeaders(
            "X-Api-Key" -> "test"
          )
          val result = route(application, request).get
          Helpers.status(result) must beEqualTo(OK)
        }
      }
    }
  }

  trait Context extends Scope {

    val adminIdentity = Fixtures.genAdminUser.sample.get
    val adminLoginInfo = LoginInfo(CredentialsProvider.ID, adminIdentity.email.value)
    val proIdentity = Fixtures.genProUser.sample.get
    val proLoginInfo = LoginInfo(CredentialsProvider.ID, proIdentity.email.value)

    val companyId = UUID.randomUUID

    implicit val env: Environment[AuthEnv] =
      new FakeEnvironment[AuthEnv](Seq(adminLoginInfo -> adminIdentity, proLoginInfo -> proIdentity))

    val mockReportRepository = mock[ReportRepository]
    mockReportRepository.getReports(any, any, any) returns Future(PaginatedResult(0, false, List()))

    class FakeModule extends AbstractModule with ScalaModule {
      override def configure() = {
        bind[Environment[AuthEnv]].toInstance(env)
        bind[ReportRepository].toInstance(mockReportRepository)
      }
    }

    lazy val application = new GuiceApplicationBuilder()
      .configure(
        Configuration(
          "play.evolutions.enabled" -> false,
          "slick.dbs.default.db.connectionPool" -> "disabled",
          "play.mailer.mock" -> true,
          "silhouette.apiKeyAuthenticator.sharedSecret" -> "sharedSecret",
          "play.tmpDirectory" -> "./target"
        )
      )
      .overrides(new FakeModule())
      .build()
  }
}
