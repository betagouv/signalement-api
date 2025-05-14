package controllers.report

import org.apache.pekko.util.Timeout
import models._
import models.company.AccessLevel
import models.report.Report
import models.report.ReportStatus
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.JsonMatchers
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._
import play.mvc.Http.Status
import utils.AppSpec
import utils.Fixtures
import utils.SIREN
import utils.TestApp
import utils.AuthHelpers._

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration._

class GetReportsByUnauthenticatedUser(implicit ee: ExecutionEnv) extends GetReportsSpec {
  override def is =
    s2"""
         Given an unauthenticated user                                ${step { someUser = None }}
         When retrieving reports                                      ${step { someResult = Some(getReports()) }}
         Then user is not authorized                                  ${userMustBeUnauthorized()}
    """
}

class GetReportsByAdminUser(implicit ee: ExecutionEnv) extends GetReportsSpec {
  override def is =
    s2"""
         Given an authenticated admin user                            ${step {
        someUser = Some(adminUser)
      }}
         When retrieving reports                                      ${step { someResult = Some(getReports()) }}
         Then reports are rendered to the user as a DGCCRF User       ${reportsMustBeRenderedForUser(adminUser)}
    """
}

class GetReportsByDGCCRFUser(implicit ee: ExecutionEnv) extends GetReportsSpec {
  override def is =
    s2"""
         Given an authenticated dgccrf user                           ${step {
        someUser = Some(dgccrfUser)
      }}
         When retrieving reports                                      ${step { someResult = Some(getReports()) }}
         Then reports are rendered to the user as an Admin            ${reportsMustBeRenderedForUser(dgccrfUser)}
    """
}

class GetReportsByProUserWithAccessToSubsidiary(implicit ee: ExecutionEnv) extends GetReportsSpec {
  override def is =
    s2"""
         Given an authenticated pro user who access to the subsidiary               ${step {
        someUser = Some(proUserWithAccessToSubsidiary)
      }}
         When retrieving reports                                                    ${step {
        someResult = Some(getReports())
      }}
         Then subsidiary reports are rendered to the user as a Pro   ${reportsMustBeRenderedForUser(
        proUserWithAccessToSubsidiary
      )}
    """
}

class GetReportsByProUserWithInvalidStatusFilter(implicit ee: ExecutionEnv) extends GetReportsSpec {
  override def is =
    s2"""
         Given an authenticated pro user                                            ${step {
        someUser = Some(proUserWithAccessToHeadOffice)
      }}
         When retrieving reports                                                    ${step {
        someResult = Some(getReports(Some("badvalue")))
      }}
         Then headOffice and subsidiary reports are rendered to the user as a Pro   ${mustBeBadRequest()}
    """
}

class GetReportsByProWithoutAccessNone(implicit ee: ExecutionEnv) extends GetReportsSpec {
  override def is =
    s2"""
         Given an authenticated pro user who only access to the subsidiary      ${step {
        someUser = Some(noAccessUser)
      }}
         When retrieving reports                                                ${step {
        someResult = Some(getReports())
      }}
         Then no reports are rendered to the user having no access              ${noReportsMustBeRendered()}
    """
}

abstract class GetReportsSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers
    with JsonMatchers {

  implicit val timeout: Timeout = 30.seconds

  lazy val userRepository          = components.userRepository
  lazy val companyRepository       = components.companyRepository
  lazy val companyAccessRepository = components.companyAccessRepository
  lazy val accessTokenRepository   = components.accessTokenRepository
  lazy val reportRepository        = components.reportRepository

  val noAccessUser                  = Fixtures.genProUser.sample.get
  val adminUser                     = Fixtures.genAdminUser.sample.get
  val dgccrfUser                    = Fixtures.genDgccrfUser.sample.get
  val proUserWithAccessToHeadOffice = Fixtures.genProUser.sample.get
  val proUserWithAccessToSubsidiary = Fixtures.genProUser.sample.get

  val standaloneCompany = Fixtures.genCompany.sample.get.copy(isHeadOffice = true, isOpen = true)
  val headOfficeCompany = Fixtures.genCompany.sample.get.copy(isHeadOffice = true, isOpen = true)
  val subsidiaryCompany =
    Fixtures.genCompany.sample.get
      .copy(siret = Fixtures.genSiret(Some(SIREN.fromSIRET(headOfficeCompany.siret))).sample.get)
      .copy(isHeadOffice = false, isOpen = true)

  val reportToStandaloneCompany = Fixtures
    .genReportForCompany(standaloneCompany)
    .sample
    .get
    .copy(employeeConsumer = false, status = ReportStatus.TraitementEnCours)
  val reportToProcessOnHeadOffice = Fixtures
    .genReportForCompany(headOfficeCompany)
    .sample
    .get
    .copy(employeeConsumer = false, status = ReportStatus.TraitementEnCours)
  val reportToProcessOnSubsidiary = Fixtures
    .genReportForCompany(subsidiaryCompany)
    .sample
    .get
    .copy(employeeConsumer = false, status = ReportStatus.TraitementEnCours)
  val reportFromEmployeeOnHeadOffice = Fixtures
    .genReportForCompany(headOfficeCompany)
    .sample
    .get
    .copy(employeeConsumer = true, status = ReportStatus.InformateurInterne, visibleToPro = false)
  val reportNAOnHeadOffice = Fixtures
    .genReportForCompany(headOfficeCompany)
    .sample
    .get
    .copy(employeeConsumer = false, status = ReportStatus.NA, visibleToPro = false)
  val reportNAOnHeadOfficeButVisible = Fixtures
    .genReportForCompany(headOfficeCompany)
    .sample
    .get
    .copy(employeeConsumer = false, status = ReportStatus.NA, visibleToPro = true)
  val allReports = Seq(
    reportToStandaloneCompany,
    reportToProcessOnHeadOffice,
    reportToProcessOnSubsidiary,
    reportFromEmployeeOnHeadOffice,
    reportNAOnHeadOffice,
    reportNAOnHeadOfficeButVisible
  )

  var someResult: Option[Result] = None
  var someUser: Option[User]     = None

  override def setupData() =
    Await.result(
      for {
        _ <- userRepository.create(noAccessUser)
        _ <- userRepository.create(adminUser)
        _ <- userRepository.create(dgccrfUser)
        _ <- userRepository.create(proUserWithAccessToHeadOffice)
        _ <- userRepository.create(proUserWithAccessToSubsidiary)

        _ <- companyRepository.getOrCreate(standaloneCompany.siret, standaloneCompany)
        _ <- companyRepository.getOrCreate(headOfficeCompany.siret, headOfficeCompany)
        _ <- companyRepository.getOrCreate(subsidiaryCompany.siret, subsidiaryCompany)

        _ <- companyAccessRepository.createAccess(
          standaloneCompany.id,
          noAccessUser.id,
          AccessLevel.NONE
        )
        _ <- companyAccessRepository.createAccess(
          headOfficeCompany.id,
          proUserWithAccessToHeadOffice.id,
          AccessLevel.MEMBER
        )
        _ <- companyAccessRepository.createAccess(
          subsidiaryCompany.id,
          proUserWithAccessToSubsidiary.id,
          AccessLevel.MEMBER
        )

        _ <- reportRepository.create(reportToStandaloneCompany)
        _ <- reportRepository.create(reportToProcessOnHeadOffice)
        _ <- reportRepository.create(reportToProcessOnSubsidiary)
        _ <- reportRepository.create(reportFromEmployeeOnHeadOffice)
        _ <- reportRepository.create(reportNAOnHeadOffice)
        _ <- reportRepository.create(reportNAOnHeadOfficeButVisible)
      } yield (),
      Duration.Inf
    )

  val (app, components) = TestApp.buildApp(
  )

  def getReports(status: Option[String] = None) = {
    val request = FakeRequest(
      play.api.http.HttpVerbs.GET,
      controllers.routes.ReportListController.searchReports().toString + status.map(x => s"?status=$x").getOrElse("")
    )
    val loggedRequest =
      someUser.map(user => request.withAuthCookie(user.email, components.cookieAuthenticator)).getOrElse(request)
    val result = route(app, loggedRequest).get

    Await.result(
      result,
      Duration.Inf
    )
  }

  def userMustBeUnauthorized() =
    someResult.isDefined mustEqual true and someResult.get.header.status === Status.UNAUTHORIZED

  def aReport(report: Report): Matcher[String] =
    /("report") / "id" andHave (report.id.toString)

  def haveReports(reports: Matcher[String]*): Matcher[String] =
    /("entities").andHave(allOf(reports: _*))

  def reportsMustBeRenderedForUser(user: User) =
    (user.userRole, user) match {
      case (UserRole.Admin, _) =>
        contentAsJson(Future.successful(someResult.get))(timeout).toString must
          /("totalCount" -> allReports.length) and
          haveReports(allReports.map(report => aReport(report)): _*)
      case (UserRole.DGCCRF, _) =>
        contentAsJson(Future.successful(someResult.get))(timeout).toString must
          /("totalCount" -> allReports.length) and
          haveReports(allReports.map(report => aReport(report)): _*)
      case (UserRole.Professionnel, pro) if pro == proUserWithAccessToSubsidiary =>
        contentAsJson(Future.successful(someResult.get))(timeout).toString must
          /("totalCount" -> 2) and
          haveReports(aReport(reportToProcessOnSubsidiary)) and
          not(
            haveReports(
              aReport(reportToProcessOnHeadOffice),
              aReport(reportFromEmployeeOnHeadOffice),
              aReport(reportNAOnHeadOffice),
              aReport(reportNAOnHeadOfficeButVisible)
            )
          )
      case (UserRole.Professionnel, pro) if pro == proUserWithAccessToSubsidiary =>
        contentAsJson(Future.successful(someResult.get))(timeout).toString must
          /("totalCount" -> 1) and
          haveReports(aReport(reportToProcessOnSubsidiary)) and
          not(
            haveReports(
              aReport(reportFromEmployeeOnHeadOffice),
              aReport(reportToProcessOnHeadOffice),
              aReport(reportNAOnHeadOffice),
              aReport(reportNAOnHeadOfficeButVisible)
            )
          )
      case _ =>
        someResult.isDefined mustEqual true and someResult.get.header.status === Status.UNAUTHORIZED
    }

  def noReportsMustBeRendered() =
    Helpers.contentAsJson(Future.successful(someResult.get))(timeout).toString must
      /("totalCount" -> 0) and
      not(haveReports(allReports.map(report => aReport(report)): _*))

  def mustBeBadRequest() =
    someResult.isDefined mustEqual true and someResult.get.header.status === Status.BAD_REQUEST
}
