package controllers

import java.time.LocalDate
import java.time.OffsetDateTime
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
import repositories._
import utils.Constants.ReportStatus._
import utils.silhouette.auth.AuthEnv
import utils.AppSpec
import utils.Fixtures

import scala.concurrent.Await
import scala.concurrent.duration._

class ReportStatisticSpec(implicit ee: ExecutionEnv) extends StatisticControllerSpec {
  override def is =
    s2"""it should
       return reports count                               ${getReportCount}
       return monthly reports count                       ${getMonthlyReportCount}
       return reports read by pro percentage              ${getReportReadByProPercentage}
       return reports forwarded to pro percentage         ${getReportForwardedToProPercentage}
       return monthly reports read by pro percentage      ${getMonthlyReportWithResponsePercentage}
       return reports with response percentage            ${getReportWithResponsePercentage}
       return monthly reports with response percentage    ${getMonthlyReportWithResponsePercentage}
       """

  def aMonthlyStat(monthlyStat: CountByDate): Matcher[String] =
    /("count").andHave(monthlyStat.count) and
      /("date").andHave(monthlyStat.date.toString)

  def haveMonthlyStats(monthlyStats: Matcher[String]*): Matcher[String] =
    have(allOf(monthlyStats: _*))

  def getReportCount = {
    val request = FakeRequest(GET, routes.StatisticController.getReportCount(None).toString)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    content must /("value" -> allReports.length)
  }

  def getMonthlyReportCount = {
    val request = FakeRequest(GET, routes.StatisticController.getCurveReportCount(None, Some(3), None).toString)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    val startDate = LocalDate.now.withDayOfMonth(1)
    content must haveMonthlyStats(
      aMonthlyStat(CountByDate(0, startDate.minusMonths(2))),
      aMonthlyStat(CountByDate(lastMonthReports.length, startDate.minusMonths(1))),
      aMonthlyStat(CountByDate(currentMonthReports.length, startDate))
    )
  }

  def getReportReadByProPercentage = {
    val request = FakeRequest(GET, routes.StatisticController.getReportReadPercentage(None).toString)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    content must /("value" -> reportsReadByProCutoff.length * 100 / reportsForwardedToProCutoff.length)
  }

  def getReportForwardedToProPercentage = {
    val request = FakeRequest(GET, routes.StatisticController.getReportForwardedPercentage(None).toString)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    content must /("value" -> reportsForwardedToProCutoff.length * 100 / (lastYearReports ::: lastMonthReports).length)
  }

  def getMonthlyReportReadByProPercentage = {
    val request =
      FakeRequest(GET, routes.StatisticController.getCurveReportReadPercentage(None, Some(2), None).toString)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    content must haveMonthlyStats(
      aMonthlyStat(CountByDate(currentMonthReportsReadByPro.length * 100 / currentMonthReports.length, LocalDate.now)),
      aMonthlyStat(
        CountByDate(lastMonthReportsWithResponse.length * 100 / lastMonthReports.length, LocalDate.now.minusMonths(1))
      )
    )
  }

  def getReportWithResponsePercentage = {
    val request = FakeRequest(GET, routes.StatisticController.getPercentageReportResponded(None).toString)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    content must /(
      "value" -> reportsWithResponseCutoff.length * 100 /
        (reportsWithResponseCutoff ::: reportsClosedByNoActionCutoff).length
    )
  }

  def getMonthlyReportWithResponsePercentage = {
    val request =
      FakeRequest(GET, routes.StatisticController.getCurveReportRespondedPercentage(None, Some(2), None).toString)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    val startDate = LocalDate.now.withDayOfMonth(1)
    content must haveMonthlyStats(
      aMonthlyStat(
        CountByDate(
          lastMonthReportsWithResponse.length * 100 /
            (lastMonthReportsWithResponse ::: lastMonthReportsClosedByNoAction).length,
          startDate.minusMonths(1)
        )
      ),
      aMonthlyStat(
        CountByDate(
          currentMonthReportsWithResponse.length * 100 /
            (currentMonthReportsSend ::: currentMonthReportsWithResponse ::: currentMonthReportsClosedByNoAction).length,
          startDate
        )
      )
    )
  }
}

abstract class StatisticControllerSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers
    with JsonMatchers {

  lazy val companyRepository = injector.instanceOf[CompanyRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]

  val company = Fixtures.genCompany.sample.get

  val lastYearReportsToProcess = Fixtures
    .genReportsForCompanyWithStatus(company, TRAITEMENT_EN_COURS)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().minusYears(1)))
  val lastYearReportsAccepted = Fixtures
    .genReportsForCompanyWithStatus(company, PROMESSE_ACTION)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().minusYears(1)))
  val lastYearReportsRejected = Fixtures
    .genReportsForCompanyWithStatus(company, SIGNALEMENT_INFONDE)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().minusYears(1)))
  val lastYearReportsNotConcerned = Fixtures
    .genReportsForCompanyWithStatus(company, SIGNALEMENT_MAL_ATTRIBUE)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().minusYears(1)))
  val lastYearReportsClosedByNoAction = Fixtures
    .genReportsForCompanyWithStatus(company, SIGNALEMENT_CONSULTE_IGNORE)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().minusYears(1)))
  val lastYearReportsNotForwarded = Fixtures
    .genReportsForCompanyWithStatus(company, NA)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().minusYears(1))) :::
    Fixtures
      .genReportsForCompanyWithStatus(company, EMPLOYEE_REPORT)
      .sample
      .get
      .map(_.copy(creationDate = OffsetDateTime.now().minusYears(1)))

  val lastYearReportsWithResponse = lastYearReportsAccepted ::: lastYearReportsRejected ::: lastYearReportsNotConcerned
  val lastYearReportsReadByPro = lastYearReportsWithResponse ::: lastYearReportsClosedByNoAction
  val lastYearReportsForwardedToPro = lastYearReportsToProcess ::: lastYearReportsReadByPro
  val lastYearReports = lastYearReportsForwardedToPro ::: lastYearReportsNotForwarded

  val lastMonthReportsToProcess = Fixtures
    .genReportsForCompanyWithStatus(company, TRAITEMENT_EN_COURS)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().minusMonths(1)))
  val lastMonthReportsAccepted = Fixtures
    .genReportsForCompanyWithStatus(company, PROMESSE_ACTION)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().minusMonths(1)))
  val lastMonthReportsRejected = Fixtures
    .genReportsForCompanyWithStatus(company, SIGNALEMENT_INFONDE)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().minusMonths(1)))
  val lastMonthReportsNotConcerned = Fixtures
    .genReportsForCompanyWithStatus(company, SIGNALEMENT_MAL_ATTRIBUE)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().minusMonths(1)))
  val lastMonthReportsClosedByNoAction = Fixtures
    .genReportsForCompanyWithStatus(company, SIGNALEMENT_CONSULTE_IGNORE)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().minusMonths(1)))
  val lastMonthReportsNotForwarded = Fixtures
    .genReportsForCompanyWithStatus(company, NA)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().minusMonths(1))) :::
    Fixtures
      .genReportsForCompanyWithStatus(company, EMPLOYEE_REPORT)
      .sample
      .get
      .map(_.copy(creationDate = OffsetDateTime.now().minusMonths(1)))

  val lastMonthReportsWithResponse =
    lastMonthReportsAccepted ::: lastMonthReportsRejected ::: lastMonthReportsNotConcerned
  val lastMonthReportsReadByPro = lastMonthReportsWithResponse ::: lastMonthReportsClosedByNoAction
  val lastMonthReportsForwardedToPro = lastMonthReportsToProcess ::: lastMonthReportsReadByPro
  val lastMonthReports = lastMonthReportsForwardedToPro ::: lastMonthReportsNotForwarded

  val currentMonthReportsToProcess = Fixtures.genReportsForCompanyWithStatus(company, TRAITEMENT_EN_COURS).sample.get
  val currentMonthReportsSend = Fixtures.genReportsForCompanyWithStatus(company, SIGNALEMENT_TRANSMIS).sample.get
  val currentMonthReportsAccepted = Fixtures.genReportsForCompanyWithStatus(company, PROMESSE_ACTION).sample.get
  val currentMonthReportsRejected = Fixtures.genReportsForCompanyWithStatus(company, SIGNALEMENT_INFONDE).sample.get
  val currentMonthReportsNotConcerned =
    Fixtures.genReportsForCompanyWithStatus(company, SIGNALEMENT_MAL_ATTRIBUE).sample.get
  val currentMonthReportsClosedByNoAction =
    Fixtures.genReportsForCompanyWithStatus(company, SIGNALEMENT_CONSULTE_IGNORE).sample.get
  val currentMonthReportsNotForwarded = Fixtures.genReportsForCompanyWithStatus(company, NA).sample.get ::: Fixtures
    .genReportsForCompanyWithStatus(company, EMPLOYEE_REPORT)
    .sample
    .get

  val currentMonthReportsWithResponse =
    currentMonthReportsAccepted ::: currentMonthReportsRejected ::: currentMonthReportsNotConcerned
  val currentMonthReportsReadByPro = currentMonthReportsWithResponse ::: currentMonthReportsClosedByNoAction
  val currentMonthReports =
    currentMonthReportsToProcess ::: currentMonthReportsSend ::: currentMonthReportsReadByPro ::: currentMonthReportsNotForwarded

  val reportsWithResponseCutoff = lastYearReportsWithResponse ::: lastMonthReportsWithResponse
  val reportsReadByProCutoff = lastYearReportsReadByPro ::: lastMonthReportsReadByPro
  val reportsForwardedToProCutoff = lastYearReportsForwardedToPro ::: lastMonthReportsForwardedToPro
  val reportsClosedByNoActionCutoff = lastYearReportsClosedByNoAction ::: lastMonthReportsClosedByNoAction

  val allReports = lastYearReports ::: lastMonthReports ::: currentMonthReports

  override def setupData() = {
    Await.result(companyRepository.getOrCreate(company.siret, company), Duration.Inf)
    for (report <- allReports)
      Await.result(reportRepository.create(report), Duration.Inf)
  }

  override def configureFakeModule(): AbstractModule =
    new FakeModule

  def loginInfo(user: User) = LoginInfo(CredentialsProvider.ID, user.email.value)

  implicit val env = new FakeEnvironment[AuthEnv](Seq().map(user => loginInfo(user) -> user))

  class FakeModule extends AppFakeModule {
    override def configure() = {
      super.configure
      bind[Environment[AuthEnv]].toInstance(env)
    }
  }
}
