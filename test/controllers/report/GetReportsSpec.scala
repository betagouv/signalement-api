package controllers.report

import akka.util.Timeout
import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.{FakeEnvironment, _}
import controllers.ReportListController
import models._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.{FutureMatchers, JsonMatchers, Matcher, JsonType}
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import play.mvc.Http.Status
import repositories._
import utils.Constants.ReportStatus._
import utils.silhouette.auth.AuthEnv
import utils.{AppSpec, Fixtures}

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
         Then reports are rendered to the user as a DGCCRF User       ${reportsMustBeRenderedForUserRole(UserRoles.Admin)}
    """
}

class GetReportsByDGCCRFUser(implicit ee: ExecutionEnv) extends GetReportsSpec  {
  override def is =
    s2"""
         Given an authenticated dgccrf user                           ${step(someLoginInfo = Some(loginInfo(dgccrfUser)))}
         When retrieving reports                                      ${step(someResult = Some(getReports()))}
         Then reports are rendered to the user as an Admin            ${reportsMustBeRenderedForUserRole(UserRoles.DGCCRF)}
    """
}

class GetReportsByProUser(implicit ee: ExecutionEnv) extends GetReportsSpec  {
  override def is =
    s2"""
         Given an authenticated pro user                              ${step(someLoginInfo = Some(loginInfo(proAdminUser)))}
         When retrieving reports                                      ${step(someResult = Some(getReports()))}
         Then reports are rendered to the user as a Pro               ${reportsMustBeRenderedForUserRole(UserRoles.Pro)}
    """
}

class GetReportsByAnotherProUser(implicit ee: ExecutionEnv) extends GetReportsSpec  {
  override def is =
    s2"""
         Given an authenticated pro user not concened by reports      ${step(someLoginInfo = Some(loginInfo(anotherProUser)))}
         When retrieving reports                                      ${step(someResult = Some(getReports()))}
         No reports are rendered                                      ${noReportsMustBeRenderedForUserRole(UserRoles.Pro)}
    """
}

abstract class GetReportsSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers with JsonMatchers {

  implicit val timeout: Timeout = 30.seconds

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val companyRepository = injector.instanceOf[CompanyRepository]
  lazy val companyAccessRepository = injector.instanceOf[CompanyAccessRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]

  val adminUser = Fixtures.genAdminUser.sample.get
  val dgccrfUser = Fixtures.genDgccrfUser.sample.get
  val proAdminUser = Fixtures.genProUser.sample.get
  val anotherProUser = Fixtures.genProUser.sample.get
  val company = Fixtures.genCompany.sample.get
  val anotherCompany = Fixtures.genCompany.sample.get

  val reportToProcess = Fixtures.genReportForCompany(company).sample.get.copy(employeeConsumer = false, status = A_TRAITER)
  val reportFromEmployee = Fixtures.genReportForCompany(company).sample.get.copy(employeeConsumer = true, status = EMPLOYEE_REPORT)
  val reportNA = Fixtures.genReportForCompany(company).sample.get.copy(employeeConsumer = false, status = NA)

  var someResult: Option[Result] = None
  var someLoginInfo: Option[LoginInfo] = None

  override def setupData = {
    Await.result(for {
      _ <- userRepository.create(adminUser)
      _ <- userRepository.create(dgccrfUser)
      admin <- userRepository.create(proAdminUser)
      anotherAdmin <- userRepository.create(anotherProUser)
      company <- companyRepository.getOrCreate(company.siret, company)
      anotherCompany <- companyRepository.getOrCreate(anotherCompany.siret, anotherCompany)
      _ <- companyRepository.setUserLevel(company, admin, AccessLevel.ADMIN)
      _ <- companyRepository.setUserLevel(anotherCompany, anotherAdmin, AccessLevel.ADMIN)
      _ <- reportRepository.create(reportToProcess)
      _ <- reportRepository.create(reportFromEmployee)
      _ <- reportRepository.create(reportNA)
    } yield Unit,
      Duration.Inf)
  }
  override def configureFakeModule(): AbstractModule = {
    new FakeModule
  }

  def loginInfo(user: User) = LoginInfo(CredentialsProvider.ID, user.email.value)

  implicit val env = new FakeEnvironment[AuthEnv](Seq(adminUser, dgccrfUser, proAdminUser, anotherProUser).map(
    user => loginInfo(user) -> user
  ))

  class FakeModule extends AppFakeModule {
    override def configure() = {
      super.configure
      bind[Environment[AuthEnv]].toInstance(env)
    }
  }

  def getReports() =  {
    Await.result(
      app.injector.instanceOf[ReportListController].getReports(None, None, None, None, None, None, None, None, None, None, None)
        .apply(someLoginInfo.map(FakeRequest().withAuthenticator[AuthEnv](_)).getOrElse(FakeRequest())),
      Duration.Inf
    )
  }

  def userMustBeUnauthorized() = {
    someResult must beSome and someResult.get.header.status === Status.UNAUTHORIZED
  }

  def reportsMustBeRenderedForUserRole(userRole: UserRole) = {

    implicit val someUserRole = Some(userRole)

    def aReport(report: Report): Matcher[String] =
      /("report") /("id") andHave(report.id.toString)

    def haveReports(reports: Matcher[String]*): Matcher[String] =
      /("entities").andHave(allOf(reports:_*))

     userRole match {
      case UserRoles.Admin =>
        contentAsJson(Future(someResult.get)).toString must
          /("totalCount" -> 3) and
          haveReports(aReport(reportFromEmployee), aReport(reportToProcess))
      case UserRoles.DGCCRF =>
        contentAsJson(Future(someResult.get)).toString must
          /("totalCount" -> 3) and
          haveReports( aReport(reportFromEmployee), aReport(reportToProcess))
      case UserRoles.Pro =>
        contentAsJson(Future(someResult.get)).toString must
          /("totalCount" -> 1) and
          haveReports(aReport(reportToProcess)) and
          not(haveReports(aReport(reportFromEmployee)))
      case _ =>
        someResult must beSome and someResult.get.header.status === Status.UNAUTHORIZED
    }
  }

  def noReportsMustBeRenderedForUserRole(userRole: UserRole) = {
    implicit val someUserRole = Some(userRole)
    someResult must beSome and contentAsJson(Future(someResult.get)) === Json.toJson(PaginatedResult[Report](0, false, List()))
  }

}