package controllers

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test._
import models._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.JsonMatchers
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._
import repositories.CompanyRepository
import repositories._
import utils.AppSpec
import utils.Fixtures
import utils.URL
import utils.silhouette.auth.AuthEnv

import scala.concurrent.Await
import scala.concurrent.duration._

class BaseWebsiteControllerSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers
    with JsonMatchers {

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]
  lazy val companyRepository = injector.instanceOf[CompanyRepository]

  val adminUser = Fixtures.genAdminUser.sample.get
  val company = Fixtures.genCompany.sample.get
  val website1 = Fixtures.genWebsiteURL.sample.get
  val website2 = Fixtures.genWebsiteURL.sample.get
  val websiteWithCompany = Fixtures.genWebsiteURL.sample.get

  override def setupData =
    Await.result(
      for {
        _ <- userRepository.create(adminUser)
        c <- companyRepository.getOrCreate(company.siret, company)
        _ <-
          reportRepository.create(Fixtures.genDraftReport.sample.get.copy(websiteURL = Some(website1)).generateReport)
        _ <-
          reportRepository.create(Fixtures.genDraftReport.sample.get.copy(websiteURL = Some(website2)).generateReport)
        _ <- reportRepository.create(
               Fixtures.genDraftReport.sample.get.copy(websiteURL = Some(URL(s"${website2}/test?query"))).generateReport
             )
        _ <- reportRepository.create(
               Fixtures.genReportForCompany(c).sample.get.copy(websiteURL = Some(websiteWithCompany))
             )
      } yield Unit,
      Duration.Inf
    )
  override def configureFakeModule(): AbstractModule =
    new FakeModule

  def loginInfo(user: User) = LoginInfo(CredentialsProvider.ID, user.email.value)

  implicit val env = new FakeEnvironment[AuthEnv](Seq(adminUser).map(user => loginInfo(user) -> user))

  class FakeModule extends AppFakeModule {
    override def configure() = {
      super.configure
      bind[Environment[AuthEnv]].toInstance(env)
    }
  }
}

class FetchUnregisteredHostSpec(implicit ee: ExecutionEnv) extends BaseWebsiteControllerSpec {
  override def is = s2"""

The fetch unregistered host endpoint should
  list website reports count with no company group by host $e1
                                                    """

  def e1 = {
    val request = FakeRequest(GET, routes.WebsiteController.fetchUnregisteredHost(None, None, None).toString)
      .withAuthenticator[AuthEnv](loginInfo(adminUser))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    content must haveCountsByHost(
      aCountByHost(website1.getHost.get, 1),
      aCountByHost(website2.getHost.get, 2)
    )
  }

  def aCountByHost(host: String, count: Int): Matcher[String] =
    /("host").andHave(host) and
      /("count").andHave(count)

  def haveCountsByHost(countsByHost: Matcher[String]*): Matcher[String] =
    have(allOf(countsByHost: _*))
}
