package controllers.report

import akka.util.Timeout
import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.{FakeEnvironment, _}
import controllers.ReportListController
import models._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.{FutureMatchers, JsonMatchers, Matcher}
import org.specs2.mutable.Specification
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import play.mvc.Http.Status
import repositories._
import utils.Constants.ReportStatus._
import utils.silhouette.auth.AuthEnv
import utils.{AppSpec, Fixtures, SIREN}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}


class GetReportsByUnauthenticatedUser(implicit ee: ExecutionEnv) extends GetReportsSpec  {
  override def is =
    s2"""
         Given an unauthenticated user                                ${step(someLoginInfo = None)}
         When retrieving reports                                      ${step(someResult = Some(getReports()))}
         Then user is not authorized                                  ${userMustBeUnauthorized}
    """
}

class GetReportsByAdminUser(implicit ee: ExecutionEnv) extends GetReportsSpec  {
  override def is =
    s2"""
         Given an authenticated admin user                            ${step(someLoginInfo = Some(loginInfo(adminUser)))}
         When retrieving reports                                      ${step(someResult = Some(getReports()))}
         Then reports are rendered to the user as a DGCCRF User       ${reportsMustBeRenderedForUser(adminUser)}
    """
}

class GetReportsByDGCCRFUser(implicit ee: ExecutionEnv) extends GetReportsSpec  {
  override def is =
    s2"""
         Given an authenticated dgccrf user                           ${step(someLoginInfo = Some(loginInfo(dgccrfUser)))}
         When retrieving reports                                      ${step(someResult = Some(getReports()))}
         Then reports are rendered to the user as an Admin            ${reportsMustBeRenderedForUser(dgccrfUser)}
    """
}

class GetReportsByProUserWithAccessToHeadOffice(implicit ee: ExecutionEnv) extends GetReportsSpec  {
  override def is =
    s2"""
         Given an authenticated pro user who access to the headOffice               ${step(someLoginInfo = Some(loginInfo(proUserWithAccessToHeadOffice)))}
         When retrieving reports                                                    ${step(someResult = Some(getReports()))}
         Then headOffice and subsidiary reports are rendered to the user as a Pro   ${reportsMustBeRenderedForUser(proUserWithAccessToHeadOffice)}
    """
}

class GetReportsByProUserWithInvalidStatusFilter(implicit ee: ExecutionEnv) extends GetReportsSpec  {
  override def is =
    s2"""
         Given an authenticated pro user                                            ${step(someLoginInfo = Some(loginInfo(proUserWithAccessToHeadOffice)))}
         When retrieving reports                                                    ${step(someResult = Some(getReports(Some("badvalue"))))}
         Then headOffice and subsidiary reports are rendered to the user as a Pro   ${noReportsMustBeRendered}
    """
}

class GetReportsByProWithAccessToSubsidiary(implicit ee: ExecutionEnv) extends GetReportsSpec  {
  override def is =
    s2"""
         Given an authenticated pro user who only access to the subsidiary      ${step(someLoginInfo = Some(loginInfo(proUserWithAccessToSubsidiary)))}
         When retrieving reports                                                ${step(someResult = Some(getReports()))}
         Then subsidiary reports are rendered to the user as a Pro              ${reportsMustBeRenderedForUser(proUserWithAccessToSubsidiary)}
    """
}

abstract class GetReportsSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers with JsonMatchers {

  implicit val timeout: Timeout = 30.seconds

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val companyRepository = injector.instanceOf[CompanyRepository]
  lazy val companyDataRepository = injector.instanceOf[CompanyDataRepository]
  lazy val accessTokenRepository = injector.instanceOf[AccessTokenRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]

  val adminUser = Fixtures.genAdminUser.sample.get
  val dgccrfUser = Fixtures.genDgccrfUser.sample.get
  val proUserWithAccessToHeadOffice = Fixtures.genProUser.sample.get
  val proUserWithAccessToSubsidiary = Fixtures.genProUser.sample.get

  val headOfficeCompany = Fixtures.genCompany.sample.get
  val subsidiaryCompany = Fixtures.genCompany.sample.get.copy(siret = Fixtures.genSiret(Some(SIREN(headOfficeCompany.siret))).sample.get)

  val headOfficeCompanyData = Fixtures.genCompanyData(Some(headOfficeCompany)).sample.get.copy(etablissementSiege = Some("true"))
  val subsidiaryCompanyData = Fixtures.genCompanyData(Some(subsidiaryCompany)).sample.get

  val reportToProcessOnHeadOffice = Fixtures.genReportForCompany(headOfficeCompany).sample.get.copy(employeeConsumer = false, status = TRAITEMENT_EN_COURS)
  val reportToProcessOnSubsidiary = Fixtures.genReportForCompany(subsidiaryCompany).sample.get.copy(employeeConsumer = false, status = TRAITEMENT_EN_COURS)
  val reportFromEmployeeOnHeadOffice = Fixtures.genReportForCompany(headOfficeCompany).sample.get.copy(employeeConsumer = true, status = EMPLOYEE_REPORT)
  val reportNAOnHeadOffice = Fixtures.genReportForCompany(headOfficeCompany).sample.get.copy(employeeConsumer = false, status = NA)
  val allReports = Seq(reportToProcessOnHeadOffice, reportToProcessOnSubsidiary, reportFromEmployeeOnHeadOffice, reportNAOnHeadOffice)

  var someResult: Option[Result] = None
  var someLoginInfo: Option[LoginInfo] = None

  override def setupData = {
    Await.result(for {
      _ <- userRepository.create(adminUser)
      _ <- userRepository.create(dgccrfUser)
      _ <- userRepository.create(proUserWithAccessToHeadOffice)
      _ <- userRepository.create(proUserWithAccessToSubsidiary)

      _ <- companyRepository.getOrCreate(headOfficeCompany.siret, headOfficeCompany)
      _ <- companyRepository.getOrCreate(subsidiaryCompany.siret, subsidiaryCompany)

      _ <- companyRepository.setUserLevel(headOfficeCompany, proUserWithAccessToHeadOffice, AccessLevel.MEMBER)
      _ <- companyRepository.setUserLevel(subsidiaryCompany, proUserWithAccessToSubsidiary, AccessLevel.MEMBER)

      _ <- companyDataRepository.create(headOfficeCompanyData)
      _ <- companyDataRepository.create(subsidiaryCompanyData)

      _ <- reportRepository.create(reportToProcessOnHeadOffice)
      _ <- reportRepository.create(reportToProcessOnSubsidiary)
      _ <- reportRepository.create(reportFromEmployeeOnHeadOffice)
      _ <- reportRepository.create(reportNAOnHeadOffice)
    } yield Unit,
      Duration.Inf)
  }

  override def cleanupData = {
    Await.result(for {
      _ <- companyDataRepository.delete(headOfficeCompanyData.id)
      _ <- companyDataRepository.delete(subsidiaryCompanyData.id)
    } yield Unit,
      Duration.Inf)
  }

  override def configureFakeModule(): AbstractModule = {
    new FakeModule
  }

  def loginInfo(user: User) = LoginInfo(CredentialsProvider.ID, user.email.value)

  implicit val env = new FakeEnvironment[AuthEnv](Seq(adminUser, dgccrfUser, proUserWithAccessToHeadOffice, proUserWithAccessToSubsidiary).map(
    user => loginInfo(user) -> user
  ))

  class FakeModule extends AppFakeModule {
    override def configure() = {
      super.configure
      bind[Environment[AuthEnv]].toInstance(env)
    }
  }

  def getReports(status: Option[String] = None) =  {
    Await.result(
      app.injector.instanceOf[ReportListController].getReports(
        offset = None,
        limit = None,
        departments = None,
        websiteURL = None,
        phone = None,
        websiteExists = None,
        phoneExists = None,
        email = None,
        siretSirenList = Nil,
        companyName = None,
        companyCountries = None,
        start = None,
        end = None,
        category = None,
        status = status,
        details = None,
        hasCompany = None,
        tags = Nil
      )
        .apply(someLoginInfo.map(FakeRequest().withAuthenticator[AuthEnv](_)).getOrElse(FakeRequest())),
      Duration.Inf
    )
  }

  def userMustBeUnauthorized() = {
    someResult must beSome and someResult.get.header.status === Status.UNAUTHORIZED
  }

  def aReport(report: Report): Matcher[String] =
    /("report") /("id") andHave(report.id.toString)

  def haveReports(reports: Matcher[String]*): Matcher[String] =
    /("entities").andHave(allOf(reports:_*))

  def reportsMustBeRenderedForUser(user: User) = {

    implicit val someUserRole = Some(user.userRole)

    (user.userRole, user) match {
      case (UserRoles.Admin, _) =>
        contentAsJson(Future(someResult.get)).toString must
          /("totalCount" -> allReports.length) and
          haveReports(allReports.map(report => aReport(report)): _*)
      case (UserRoles.DGCCRF, _) =>
        contentAsJson(Future(someResult.get)).toString must
          /("totalCount" -> allReports.length) and
          haveReports(allReports.map(report => aReport(report)): _*)
      case (UserRoles.Pro, pro) if pro == proUserWithAccessToHeadOffice =>
        contentAsJson(Future(someResult.get)).toString must
          /("totalCount" -> 2) and
          haveReports(aReport(reportToProcessOnHeadOffice), aReport(reportToProcessOnSubsidiary)) and
          not(haveReports(aReport(reportFromEmployeeOnHeadOffice), aReport(reportNAOnHeadOffice)))
      case (UserRoles.Pro, pro) if pro == proUserWithAccessToSubsidiary =>
        contentAsJson(Future(someResult.get)).toString must
          /("totalCount" -> 1) and
          haveReports(aReport(reportToProcessOnSubsidiary)) and
          not(haveReports(aReport(reportFromEmployeeOnHeadOffice), aReport(reportToProcessOnHeadOffice), aReport(reportNAOnHeadOffice)))
      case _ =>
        someResult must beSome and someResult.get.header.status === Status.UNAUTHORIZED
    }
  }

  def noReportsMustBeRendered() = {
    contentAsJson(Future(someResult.get)).toString must
      /("totalCount" -> 0) and
      not(haveReports(allReports.map(report => aReport(report)): _*))
  }

}