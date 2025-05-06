package controllers.company

import controllers.routes
import models.company.AccessLevel
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.JsonMatchers
import org.specs2.matcher.Matcher
import org.specs2.matcher.TraversableMatchers
import org.specs2.mutable.Specification
import play.api.Logger
import play.api.test.Helpers._
import play.api.test._
import utils._
import utils.AuthHelpers._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class BaseVisibleCompaniesSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers
    with JsonMatchers {

  implicit val ec: ExecutionContext = ee.executionContext
  val logger: Logger                = Logger(this.getClass)

  lazy val userRepository                  = components.userRepository
  lazy val companyRepository               = components.companyRepository
  lazy val companyAccessRepository         = components.companyAccessRepository
  lazy val companiesVisibilityOrchestrator = components.companiesVisibilityOrchestrator

  val proUserWithAccessToHeadOffice = Fixtures.genProUser.sample.get
  val adminWithAccessToHeadOffice   = Fixtures.genProUser.sample.get
  val proUserWithAccessToSubsidiary = Fixtures.genProUser.sample.get
  val adminWithAccessToSubsidiary   = Fixtures.genProUser.sample.get

  val headOfficeCompany = Fixtures.genCompany.sample.get.copy(isHeadOffice = true)
  val subsidiaryCompany =
    Fixtures.genCompany.sample.get
      .copy(siret = Fixtures.genSiret(Some(SIREN.fromSIRET(headOfficeCompany.siret))).sample.get, isHeadOffice = false)

  val subsidiaryClosedCompany = Fixtures.genCompany.sample.get
    .copy(
      siret = SIRET.fromUnsafe(SIREN.fromSIRET(headOfficeCompany.siret).value + "00020"),
      isOpen = false
    )

  val companyWithoutAccess = Fixtures.genCompany.sample.get

  override def setupData() =
    Await.result(
      for {
        _ <- userRepository.create(proUserWithAccessToHeadOffice)
        _ <- userRepository.create(proUserWithAccessToSubsidiary)
        _ <- userRepository.create(adminWithAccessToHeadOffice)
        _ <- userRepository.create(adminWithAccessToSubsidiary)

        _ <- companyRepository.getOrCreate(headOfficeCompany.siret, headOfficeCompany)
        _ <- companyRepository.getOrCreate(subsidiaryCompany.siret, subsidiaryCompany)

        _ <- companyAccessRepository.createAccess(
          headOfficeCompany.id,
          proUserWithAccessToHeadOffice.id,
          AccessLevel.MEMBER
        )
        _ <- companyAccessRepository.createAccess(
          headOfficeCompany.id,
          adminWithAccessToHeadOffice.id,
          AccessLevel.ADMIN
        )
        _ <- companyAccessRepository.createAccess(
          subsidiaryCompany.id,
          proUserWithAccessToSubsidiary.id,
          AccessLevel.MEMBER
        )
        _ <- companyAccessRepository.createAccess(
          subsidiaryCompany.id,
          adminWithAccessToSubsidiary.id,
          AccessLevel.MEMBER
        )

        _ <- companyRepository.getOrCreate(companyWithoutAccess.siret, companyWithoutAccess)
      } yield (),
      Duration.Inf
    )

  val (app, components) = TestApp.buildApp(
  )

}

class VisibleCompaniesSpec(implicit ee: ExecutionEnv) extends BaseVisibleCompaniesSpec {
  override def is =
    s2"""

The get visible companies endpoint should
  list headOffice and subsidiary companies for a user who access to the headOffice $e1
  list only the subsidiary company for a user who only access to the subsidiary $e2
  list admins and member having direct access to the headOffice $e3
  list admins and member having access to the subsidiary including headOffices admins and members $e4
"""

  def e1 = {
    val request = FakeRequest(GET, routes.CompanyController.getCompaniesOfPro().toString)
      .withAuthCookie(proUserWithAccessToHeadOffice.email, components.cookieAuthenticator)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    content must haveVisibleCompanies(
      aVisibleCompany(headOfficeCompany.siret),
      aVisibleCompany(subsidiaryCompany.siret)
    )
  }

  def e2 = {
    val request = FakeRequest(GET, routes.CompanyController.getCompaniesOfPro().toString)
      .withAuthCookie(proUserWithAccessToSubsidiary.email, components.cookieAuthenticator)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    content must haveVisibleCompanies(
      aVisibleCompany(subsidiaryCompany.siret)
    )
  }

  def e3 = {
    val headOfficeViewersList = Await.result(
      companiesVisibilityOrchestrator.fetchUsersWithHeadOffices(List((headOfficeCompany.siret, headOfficeCompany.id))),
      Duration.Inf
    )
    Await.result(
      companiesVisibilityOrchestrator.fetchUsersWithHeadOffices(headOfficeCompany.siret),
      Duration.Inf
    )
    headOfficeViewersList(headOfficeCompany.id).map(_.id).sorted must beEqualTo(
      List(
        adminWithAccessToHeadOffice,
        proUserWithAccessToHeadOffice
      ).map(_.id).sorted
    )
  }

  def e4 = {
    val subsidiaryViewersList = Await.result(
      companiesVisibilityOrchestrator.fetchUsersWithHeadOffices(List((subsidiaryCompany.siret, subsidiaryCompany.id))),
      Duration.Inf
    )
    val subsidiaryViewers = Await.result(
      companiesVisibilityOrchestrator.fetchUsersWithHeadOffices(subsidiaryCompany.siret),
      Duration.Inf
    )
    subsidiaryViewersList(subsidiaryCompany.id).map(_.id).sorted must beEqualTo(subsidiaryViewers.map(_.id).sorted)
    subsidiaryViewersList(subsidiaryCompany.id).map(_.id).sorted must beEqualTo(
      List(
        proUserWithAccessToHeadOffice,
        proUserWithAccessToSubsidiary,
        adminWithAccessToHeadOffice,
        adminWithAccessToSubsidiary
      ).map(_.id).sorted
    )
  }

  def aVisibleCompany(siret: SIRET): Matcher[String] =
    /("siret" -> siret.value)

  def haveVisibleCompanies(visibleCompanies: Matcher[String]*): Matcher[String] =
    have(TraversableMatchers.exactly(visibleCompanies: _*))
}
