package controllers

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.JsonMatchers
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import play.api.test.Helpers._
import play.api.test._
import utils.AppSpec
import utils.Fixtures
import utils.SIRET
import utils.TestApp
import utils.AuthHelpers._

import scala.concurrent.Await
import scala.concurrent.duration._

class BaseReportedPhoneControllerSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers
    with JsonMatchers {

  lazy val userRepository    = components.userRepository
  lazy val reportRepository  = components.reportRepository
  lazy val companyRepository = components.companyRepository

  val adminUser     = Fixtures.genAdminUser.sample.get
  val company       = Fixtures.genCompany.sample.get
  val reportedPhone = Fixtures.genReportedPhone.sample.get

  override def setupData() =
    Await.result(
      for {
        _      <- userRepository.create(adminUser)
        (c, _) <- companyRepository.getOrCreate(company.siret, company)
        _ <-
          reportRepository.create(
            Fixtures.genReportFromDraft(Fixtures.genDraftReport.sample.get.copy(phone = Some(reportedPhone)))
          )
        report2 <- reportRepository.create(Fixtures.genReportForCompany(c).sample.get.copy(phone = Some(reportedPhone)))
        _ <-
          reportRepository.create(
            Fixtures.genReportForCompany(c).sample.get.copy(phone = Some(reportedPhone), category = report2.category)
          )
      } yield (),
      Duration.Inf
    )

  val (app, components) = TestApp.buildApp(
  )

}

class FetchUnregisteredPhoneSpec(implicit ee: ExecutionEnv) extends BaseReportedPhoneControllerSpec {
  override def is = s2"""

The fetch phone group  SIRET endpoint should
  list reportedPhone reports count group by phone, category and SIRET $e1
                                                    """

  def e1 = {
    val request = FakeRequest(GET, routes.ReportedPhoneController.fetchGrouped(None, None, None, None, None).toString)
      .withAuthCookie(adminUser.email, components.cookieAuthenticator)
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
    /("entities").andHave(allOf(phoneMatchers: _*))
}
