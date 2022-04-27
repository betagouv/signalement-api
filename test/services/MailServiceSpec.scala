package services

import cats.data.NonEmptyList
import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test._
import models._
import models.report.Report
import orchestrators.CompaniesVisibilityOrchestrator
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import play.api.Logger
import repositories.company.CompanyRepository
import repositories.companyaccess.CompanyAccessRepository
import repositories.companydata.CompanyDataRepository
import repositories.reportblockednotification.ReportNotificationBlockedRepository
import repositories.user.UserRepository
import services.Email.ProNewReportNotification
import utils.AppSpec
import utils.EmailAddress
import utils.Fixtures
import utils.FrontRoute
import utils.SIREN
import utils.silhouette.auth.AuthEnv

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

class BaseMailServiceSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers
    with JsonMatchers {

  implicit val ec = ee.executionContext
  val logger: Logger = Logger(this.getClass)

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val companyRepository = injector.instanceOf[CompanyRepository]
  lazy val companyAccessRepository = injector.instanceOf[CompanyAccessRepository]
  lazy val companyDataRepository = injector.instanceOf[CompanyDataRepository]
  lazy val companiesVisibilityOrchestrator = injector.instanceOf[CompaniesVisibilityOrchestrator]
  lazy val reportNotificationBlocklistRepository = injector.instanceOf[ReportNotificationBlockedRepository]
  implicit lazy val frontRoute = injector.instanceOf[FrontRoute]
  lazy val mailerService = injector.instanceOf[MailerService]
  lazy val mailService = injector.instanceOf[MailService]

  val proWithAccessToHeadOffice = Fixtures.genProUser.sample.get
  val proWithAccessToSubsidiary = Fixtures.genProUser.sample.get

  val headOfficeCompany = Fixtures.genCompany.sample.get
  val subsidiaryCompany = Fixtures.genCompany.sample.get.copy(
    siret = Fixtures.genSiret(Some(SIREN(headOfficeCompany.siret))).sample.get
  )
  val unrelatedCompany = Fixtures.genCompany.sample.get

  val headOfficeCompanyData = Fixtures
    .genCompanyData(Some(headOfficeCompany))
    .sample
    .get
    .copy(
      etablissementSiege = Some("true")
    )
  val subsidiaryCompanyData = Fixtures.genCompanyData(Some(subsidiaryCompany)).sample.get
  val unrelatedCompanyData = Fixtures.genCompanyData(Some(unrelatedCompany)).sample.get
  val reportForSubsidiary = Fixtures.genReportForCompany(subsidiaryCompany).sample.get
  val reportForUnrelated = Fixtures.genReportForCompany(unrelatedCompany).sample.get

  override def setupData() =
    Await.result(
      for {
        _ <- userRepository.create(proWithAccessToHeadOffice)
        _ <- userRepository.create(proWithAccessToSubsidiary)

        _ <- companyRepository.getOrCreate(headOfficeCompany.siret, headOfficeCompany)
        _ <- companyRepository.getOrCreate(subsidiaryCompany.siret, subsidiaryCompany)
        _ <- companyRepository.getOrCreate(unrelatedCompany.siret, unrelatedCompany)

        _ <- companyAccessRepository.createUserAccess(
          headOfficeCompany.id,
          proWithAccessToHeadOffice.id,
          AccessLevel.MEMBER
        )
        _ <- companyAccessRepository.createUserAccess(
          subsidiaryCompany.id,
          proWithAccessToSubsidiary.id,
          AccessLevel.MEMBER
        )
        _ <- companyAccessRepository.createUserAccess(
          unrelatedCompany.id,
          proWithAccessToSubsidiary.id,
          AccessLevel.MEMBER
        )

        _ <- companyDataRepository.create(headOfficeCompanyData)
        _ <- companyDataRepository.create(subsidiaryCompanyData)
        _ <- companyDataRepository.create(unrelatedCompanyData)
      } yield (),
      Duration.Inf
    )

  override def cleanupData() =
    Await.result(
      for {
        _ <- companyDataRepository.delete(headOfficeCompanyData.id)
        _ <- companyDataRepository.delete(subsidiaryCompanyData.id)
      } yield (),
      Duration.Inf
    )

  override def configureFakeModule(): AbstractModule = new FakeModule

  def loginInfo(user: User) = LoginInfo(CredentialsProvider.ID, user.email.value)

  implicit val env = new FakeEnvironment[AuthEnv](
    Seq(proWithAccessToHeadOffice, proWithAccessToSubsidiary).map(user => loginInfo(user) -> user)
  )

  class FakeModule extends AppFakeModule {
    override def configure() = {
      super.configure()
      bind[Environment[AuthEnv]].toInstance(env)
    }
  }

  protected def sendEmail(emails: NonEmptyList[EmailAddress], report: Report) =
    Await.result(
      mailService.send(
        ProNewReportNotification(
          emails,
          report
        )
      ),
      Duration.Inf
    )

  protected def checkRecipients(expectedRecipients: Seq[EmailAddress]) =
    if (expectedRecipients.isEmpty) {
      there was no(mailerService).sendEmail(any, any, any, any, any, any)
    } else {
      there was one(mailerService).sendEmail(
        any,
        argThat((list: Seq[EmailAddress]) => list.sortBy(_.value) == expectedRecipients.sortBy(_.value)),
        any,
        any,
        any,
        any
      )
    }
}

class MailServiceSpecNoBlock(implicit ee: ExecutionEnv) extends BaseMailServiceSpec {
  override def is = s2"""Email must be sent to admin and admin of head office $e1"""
  def e1 = {
    sendEmail(NonEmptyList.of(proWithAccessToHeadOffice.email, proWithAccessToSubsidiary.email), reportForSubsidiary)
    Thread.sleep(100)
    checkRecipients(Seq(proWithAccessToHeadOffice.email, proWithAccessToSubsidiary.email))
  }
}

class MailServiceSpecSomeBlock(implicit ee: ExecutionEnv) extends BaseMailServiceSpec {
  override def is = s2"""Email must be sent only to the user that didn't block the notifications $e1"""

  def e1 = {
    Await.result(
      reportNotificationBlocklistRepository
        .create(proWithAccessToSubsidiary.id, Seq(reportForSubsidiary.companyId.get)),
      Duration.Inf
    )
    sendEmail(NonEmptyList.of(proWithAccessToHeadOffice.email, proWithAccessToSubsidiary.email), reportForSubsidiary)
    checkRecipients(Seq(proWithAccessToHeadOffice.email))
  }
}

class MailServiceSpecAllBlock(implicit ee: ExecutionEnv) extends BaseMailServiceSpec {
  override def is = s2"""No email must be sent since all users blocked the notifications $e1"""

  def e1 = {
    Await.result(
      Future.sequence(
        Seq(
          reportNotificationBlocklistRepository
            .create(proWithAccessToSubsidiary.id, Seq(reportForSubsidiary.companyId.get)),
          reportNotificationBlocklistRepository
            .create(proWithAccessToHeadOffice.id, Seq(reportForSubsidiary.companyId.get))
        )
      ),
      Duration.Inf
    )

    sendEmail(NonEmptyList.of(proWithAccessToHeadOffice.email, proWithAccessToSubsidiary.email), reportForSubsidiary)
    checkRecipients(Seq())
  }
}

//MailServiceSpecNoBlock
//MailServiceSpecSomeBlock
//MailServiceSpecAllBlock
