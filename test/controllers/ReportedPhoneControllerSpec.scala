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
import play.api.test.Helpers._
import play.api.test._
import repositories.CompanyRepository
import repositories._
import utils.silhouette.auth.AuthEnv
import utils.AppSpec
import utils.Fixtures
import utils.SIRET

import scala.concurrent.Await
import scala.concurrent.duration._

class BaseReportedPhoneControllerSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers
    with JsonMatchers {

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]
  lazy val companyRepository = injector.instanceOf[CompanyRepository]

  val adminUser = Fixtures.genAdminUser.sample.get
  val company = Fixtures.genCompany.sample.get
  val reportedPhone = Fixtures.genReportedPhone.sample.get

  override def setupData() =
    Await.result(
      for {
        _ <- userRepository.create(adminUser)
        c <- companyRepository.getOrCreate(company.siret, company)
        _ <-
          reportRepository.create(Fixtures.genDraftReport.sample.get.copy(phone = Some(reportedPhone)).generateReport)
        report2 <- reportRepository.create(Fixtures.genReportForCompany(c).sample.get.copy(phone = Some(reportedPhone)))
        _ <-
          reportRepository.create(
            Fixtures.genReportForCompany(c).sample.get.copy(phone = Some(reportedPhone), category = report2.category)
          )
      } yield (),
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

class FetchUnregisteredPhoneSpec(implicit ee: ExecutionEnv) extends BaseReportedPhoneControllerSpec {
  override def is = s2"""

The fetch phone group  SIRET endpoint should
  list reportedPhone reports count group by phone, category and SIRET $e1
                                                    """

  def e1 = {
    val request = FakeRequest(GET, routes.ReportedPhoneController.fetchGrouped(None, None, None).toString)
      .withAuthenticator[AuthEnv](loginInfo(adminUser))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    content must haveCountsByPhone(
      aCountByPhone(reportedPhone, 1),
      aCountByPhoneAndSIRET(reportedPhone, company.siret, 2)
    )
  }

  def aCountByPhone(phone: String, count: Int): Matcher[String] =
    /("phone").andHave(phone) and
      /("count").andHave(count)

  def aCountByPhoneAndSIRET(phone: String, siret: SIRET, count: Int): Matcher[String] =
    /("phone").andHave(phone) and
      /("siret").andHave(siret.toString) and
      /("count").andHave(count)

  def haveCountsByPhone(phoneMatchers: Matcher[String]*): Matcher[String] =
    have(allOf(phoneMatchers: _*))
}
