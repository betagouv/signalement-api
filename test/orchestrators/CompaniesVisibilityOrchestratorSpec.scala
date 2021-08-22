package orchestrators

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test.FakeEnvironment
import models.AccessLevel
import models.User
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import play.api.Logger
import repositories.CompanyDataRepository
import repositories.CompanyRepository
import repositories.UserRepository
import utils.silhouette.auth.AuthEnv
import utils.AppSpec
import utils.Fixtures
import utils.SIREN

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class BaseCompaniesVisibilityOrchestratorSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers
    with JsonMatchers {

  implicit val ec = ee.executionContext
  val logger: Logger = Logger(this.getClass)

  lazy val userRepo = injector.instanceOf[UserRepository]
  lazy val companyDataRepo = injector.instanceOf[CompanyDataRepository]
  lazy val companyRepo = injector.instanceOf[CompanyRepository]

  val headOfficeCompany = Fixtures.genCompany.sample.get
  val subsidiaryCompany1 = Fixtures.genCompany.sample.get.copy(
    siret = Fixtures.genSiret(Some(SIREN(headOfficeCompany.siret))).sample.get
  )
  val subsidiaryCompany2 = Fixtures.genCompany.sample.get.copy(
    siret = Fixtures.genSiret(Some(SIREN(headOfficeCompany.siret))).sample.get
  )
  val standaloneCompany = Fixtures.genCompany.sample.get.copy(
    siret = Fixtures.genSiret(Some(SIREN(headOfficeCompany.siret))).sample.get
  )
  val headOfficeCompanyData = Fixtures
    .genCompanyData(Some(headOfficeCompany))
    .sample
    .get
    .copy(
      etablissementSiege = Some("true")
    )
  val subsidiaryCompanyData1 = Fixtures.genCompanyData(Some(subsidiaryCompany1)).sample.get
  val subsidiaryCompanyData2 = Fixtures.genCompanyData(Some(subsidiaryCompany2)).sample.get
  val standaloneCompanyData = Fixtures.genCompanyData(Some(standaloneCompany)).sample.get

  val adminWithAccessToHeadOffice = Fixtures.genProUser.sample.get
  val memberWithAccessToHeadOffice = Fixtures.genProUser.sample.get
  val adminWithAccessToSubsidiary = Fixtures.genProUser.sample.get
  val adminWithAccessToStandalone = Fixtures.genProUser.sample.get
  val proWithoutAccess = Fixtures.genProUser.sample.get

  override def setupData = Await.result(
    for {
      _ <- userRepo.create(adminWithAccessToHeadOffice)
      _ <- userRepo.create(memberWithAccessToHeadOffice)
      _ <- userRepo.create(adminWithAccessToSubsidiary)
      _ <- userRepo.create(adminWithAccessToStandalone)
      _ <- userRepo.create(proWithoutAccess)

      _ <- companyRepo.getOrCreate(headOfficeCompany.siret, headOfficeCompany)
      _ <- companyRepo.getOrCreate(subsidiaryCompany1.siret, subsidiaryCompany1)
      _ <- companyRepo.getOrCreate(subsidiaryCompany2.siret, subsidiaryCompany2)
      _ <- companyRepo.getOrCreate(standaloneCompany.siret, standaloneCompany)

      _ <- companyRepo.setUserLevel(headOfficeCompany, adminWithAccessToHeadOffice, AccessLevel.ADMIN)
      _ <- companyRepo.setUserLevel(headOfficeCompany, memberWithAccessToHeadOffice, AccessLevel.MEMBER)
      _ <- companyRepo.setUserLevel(subsidiaryCompany1, adminWithAccessToSubsidiary, AccessLevel.ADMIN)
      _ <- companyRepo.setUserLevel(standaloneCompany, adminWithAccessToStandalone, AccessLevel.ADMIN)

      _ <- companyDataRepo.create(headOfficeCompanyData)
      _ <- companyDataRepo.create(subsidiaryCompanyData1)
      _ <- companyDataRepo.create(subsidiaryCompanyData2)
      _ <- companyDataRepo.create(standaloneCompanyData)
    } yield (),
    Duration.Inf
  )

  override def cleanupData =
    Await.result(
      for {
        _ <- companyDataRepo.delete(headOfficeCompanyData.id)
        _ <- companyDataRepo.delete(subsidiaryCompanyData1.id)
        _ <- companyDataRepo.delete(subsidiaryCompanyData2.id)
        _ <- companyDataRepo.delete(standaloneCompanyData.id)
      } yield (),
      Duration.Inf
    )

  def loginInfo(user: User) = LoginInfo(CredentialsProvider.ID, user.email.value)

  implicit val env = new FakeEnvironment[AuthEnv](
    Seq(adminWithAccessToHeadOffice, adminWithAccessToSubsidiary).map(user => loginInfo(user) -> user)
  )

  class FakeModule extends AppFakeModule {
    override def configure() = {
      super.configure
      bind[Environment[AuthEnv]].toInstance(env)
    }
  }

  override def configureFakeModule(): AbstractModule = new FakeModule
}
