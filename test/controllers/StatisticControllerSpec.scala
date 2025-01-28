package controllers

import models._
import models.report.ReportStatus
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.JsonMatchers
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import play.api.test.Helpers._
import play.api.test._
import utils.AppSpec
import utils.Fixtures
import utils.TestApp
import utils.AuthHelpers._

import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.time.OffsetDateTime
import scala.concurrent.Await
import scala.concurrent.duration._

class ReportStatisticSpec extends StatisticControllerSpec {
  override def is =
    s2"""it should
       return reports count                               ${getReportCount}
       return reports curve                               ${getReportsCurve}
       return reports curve filted by status              ${getReportsCurveFilteredByStatus}
       """

  def aMonthlyStat(monthlyStat: CountByDate): Matcher[String] =
    /("count").andHave(monthlyStat.count) and
      /("date").andHave(monthlyStat.date.toString)

  def haveMonthlyStats(monthlyStats: Matcher[String]*): Matcher[String] =
    have(allOf(monthlyStats: _*))

  def getReportCount = {
    val request = FakeRequest(GET, routes.StatisticController.getReportsCount().toString)
      .withAuthCookie(adminUser.email, components.cookieAuthenticator)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    content must /("value" -> allReports.length)
  }

  def getReportsCurve = {
    val request = FakeRequest(GET, routes.StatisticController.getReportsCountCurve().toString)
      .withAuthCookie(adminUser.email, components.cookieAuthenticator)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content   = contentAsJson(result).toString
    val startDate = LocalDate.now().withDayOfMonth(1)
    content must haveMonthlyStats(
      aMonthlyStat(CountByDate(0, startDate.minusMonths(2L))),
      aMonthlyStat(CountByDate(lastMonthReports.length, startDate.minusMonths(1L))),
      aMonthlyStat(CountByDate(currentMonthReports.length, startDate))
    )
  }

  def getReportsCurveFilteredByStatus = {
    val request =
      FakeRequest(
        GET,
        routes.StatisticController
          .getReportsCountCurve()
          .toString + "?status=PromesseAction&status=Infonde&status=MalAttribue"
      )
        .withAuthCookie(adminUser.email, components.cookieAuthenticator)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content   = contentAsJson(result).toString
    val startDate = LocalDate.now().withDayOfMonth(1)
    content must haveMonthlyStats(
      aMonthlyStat(CountByDate(lastMonthReportsWithResponse.length, startDate.minusMonths(1L))),
      aMonthlyStat(CountByDate(currentMonthReportsWithResponse.length, startDate))
    )
  }

}

abstract class StatisticControllerSpec extends Specification with AppSpec with FutureMatchers with JsonMatchers {

  lazy val companyRepository = components.companyRepository
  lazy val userRepository    = components.userRepository
  lazy val reportRepository  = components.reportRepository

  val adminUser = Fixtures.genAdminUser.sample.get

  val company = Fixtures.genCompany.sample.get

  val lastYearReportsToProcess = Fixtures
    .genReportsForCompanyWithStatus(company, ReportStatus.TraitementEnCours)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusYears(1)))
  val lastYearReportsAccepted = Fixtures
    .genReportsForCompanyWithStatus(company, ReportStatus.PromesseAction)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusYears(1)))
  val lastYearReportsRejected = Fixtures
    .genReportsForCompanyWithStatus(company, ReportStatus.Infonde)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusYears(1)))
  val lastYearReportsNotConcerned = Fixtures
    .genReportsForCompanyWithStatus(company, ReportStatus.MalAttribue)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusYears(1)))
  val lastYearReportsClosedByNoAction = Fixtures
    .genReportsForCompanyWithStatus(company, ReportStatus.ConsulteIgnore)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusYears(1)))
  val lastYearReportsNotForwarded = Fixtures
    .genReportsForCompanyWithStatus(company, ReportStatus.NA)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusYears(1))) :::
    Fixtures
      .genReportsForCompanyWithStatus(company, ReportStatus.InformateurInterne)
      .sample
      .get
      .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusYears(1)))

  val lastYearReportsWithResponse = lastYearReportsAccepted ::: lastYearReportsRejected ::: lastYearReportsNotConcerned
  val lastYearReportsReadByPro    = lastYearReportsWithResponse ::: lastYearReportsClosedByNoAction
  val lastYearReportsForwardedToPro = lastYearReportsToProcess ::: lastYearReportsReadByPro
  val lastYearReports               = lastYearReportsForwardedToPro ::: lastYearReportsNotForwarded

  val lastMonthReportsToProcess = Fixtures
    .genReportsForCompanyWithStatus(company, ReportStatus.TraitementEnCours)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusMonths(1L)))
  val lastMonthReportsAccepted = Fixtures
    .genReportsForCompanyWithStatus(company, ReportStatus.PromesseAction)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusMonths(1L)))
  val lastMonthReportsRejected = Fixtures
    .genReportsForCompanyWithStatus(company, ReportStatus.Infonde)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusMonths(1L)))
  val lastMonthReportsNotConcerned = Fixtures
    .genReportsForCompanyWithStatus(company, ReportStatus.MalAttribue)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusMonths(1L)))
  val lastMonthReportsClosedByNoAction = Fixtures
    .genReportsForCompanyWithStatus(company, ReportStatus.ConsulteIgnore)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusMonths(1L)))
  val lastMonthReportsNotForwarded = Fixtures
    .genReportsForCompanyWithStatus(company, ReportStatus.NA)
    .sample
    .get
    .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusMonths(1L))) :::
    Fixtures
      .genReportsForCompanyWithStatus(company, ReportStatus.InformateurInterne)
      .sample
      .get
      .map(_.copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusMonths(1L)))

  val lastMonthReportsWithResponse =
    lastMonthReportsAccepted ::: lastMonthReportsRejected ::: lastMonthReportsNotConcerned
  val lastMonthReportsReadByPro      = lastMonthReportsWithResponse ::: lastMonthReportsClosedByNoAction
  val lastMonthReportsForwardedToPro = lastMonthReportsToProcess ::: lastMonthReportsReadByPro
  val lastMonthReports               = lastMonthReportsForwardedToPro ::: lastMonthReportsNotForwarded

  val currentMonthReportsToProcess =
    Fixtures.genReportsForCompanyWithStatus(company, ReportStatus.TraitementEnCours).sample.get
  val currentMonthReportsSend = Fixtures.genReportsForCompanyWithStatus(company, ReportStatus.Transmis).sample.get
  val currentMonthReportsAccepted =
    Fixtures.genReportsForCompanyWithStatus(company, ReportStatus.PromesseAction).sample.get
  val currentMonthReportsRejected = Fixtures.genReportsForCompanyWithStatus(company, ReportStatus.Infonde).sample.get
  val currentMonthReportsNotConcerned =
    Fixtures.genReportsForCompanyWithStatus(company, ReportStatus.MalAttribue).sample.get
  val currentMonthReportsClosedByNoAction =
    Fixtures.genReportsForCompanyWithStatus(company, ReportStatus.ConsulteIgnore).sample.get
  val currentMonthReportsNotForwarded =
    Fixtures.genReportsForCompanyWithStatus(company, ReportStatus.NA).sample.get ::: Fixtures
      .genReportsForCompanyWithStatus(company, ReportStatus.InformateurInterne)
      .sample
      .get

  val currentMonthReportsWithResponse =
    currentMonthReportsAccepted ::: currentMonthReportsRejected ::: currentMonthReportsNotConcerned
  val currentMonthReportsReadByPro = currentMonthReportsWithResponse ::: currentMonthReportsClosedByNoAction
  val currentMonthReports =
    currentMonthReportsToProcess ::: currentMonthReportsSend ::: currentMonthReportsReadByPro ::: currentMonthReportsNotForwarded

  val reportsWithResponseCutoff     = lastYearReportsWithResponse ::: lastMonthReportsWithResponse
  val reportsReadByProCutoff        = lastYearReportsReadByPro ::: lastMonthReportsReadByPro
  val reportsForwardedToProCutoff   = lastYearReportsForwardedToPro ::: lastMonthReportsForwardedToPro
  val reportsClosedByNoActionCutoff = lastYearReportsClosedByNoAction ::: lastMonthReportsClosedByNoAction

  val allReports = lastYearReports ::: lastMonthReports ::: currentMonthReports

  override def setupData() = {
    Await.result(userRepository.create(adminUser), Duration.Inf)
    Await.result(companyRepository.getOrCreate(company.siret, company), Duration.Inf)
    for (report <- allReports)
      Await.result(reportRepository.create(report), Duration.Inf)
  }

  val (app, components) = TestApp.buildApp(
  )
}
