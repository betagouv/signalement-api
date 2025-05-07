package orchestrators

import models.company.AccessLevel
import models.company.CompanyCreation
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.when
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito.mock
import org.specs2.mutable.Specification
import repositories.company.CompanyRepositoryInterface
import repositories.website.WebsiteRepositoryInterface
import tasks.company.CompanySyncServiceInterface
import utils.Fixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ImportOrchestratorSpec extends Specification with FutureMatchers {

  def argMatching[T](pf: PartialFunction[Any, Unit]) = argThat[T](pf.isDefinedAt(_))

  "ImportOrchestrator" should {
    "import users" should {
      "import a siren for 3 users (1 not existing)" in {
        val companyRepository          = mock[CompanyRepositoryInterface]
        val companySyncService         = mock[CompanySyncServiceInterface]
        val userOrchestrator           = mock[UserOrchestratorInterface]
        val companyOrchestrator        = mock[CompanyOrchestrator]
        val proAccessTokenOrchestrator = mock[ProAccessTokenOrchestratorInterface]
        val websiteRepository          = mock[WebsiteRepositoryInterface]
        val websitesOrchestrator       = mock[WebsitesOrchestrator]

        val importOrchestrator =
          new ImportOrchestrator(
            companyRepository,
            companySyncService,
            userOrchestrator,
            companyOrchestrator,
            proAccessTokenOrchestrator,
            websiteRepository,
            websitesOrchestrator
          )

        val siren                = Fixtures.genSiren.sample.get
        val existingUser1        = Fixtures.genUser.sample.get
        val existingUser2        = Fixtures.genUser.sample.get
        val user3                = Fixtures.genUser.sample.get
        val existingCompanySR1   = Fixtures.genCompanySearchResult(Some(siren)).sample.get
        val existingCompanySR2   = Fixtures.genCompanySearchResult(Some(siren)).sample.get
        val companySR3           = Fixtures.genCompanySearchResult(Some(siren)).sample.get
        val companySearchResults = List(existingCompanySR1, existingCompanySR2, companySR3)
        val existingCompany1     = existingCompanySR1.toCreation.toCompany()
        val existingCompany2     = existingCompanySR2.toCreation.toCompany()
        val company3             = companySR3.toCreation.toCompany()
        val users                = List(existingUser1, existingUser2, user3)

        when(companyRepository.findBySirets(List.empty)).thenReturn(Future.successful(List.empty))
        when(companySyncService.companiesBySirets(List.empty)).thenReturn(Future.successful(List.empty))
        when(companySyncService.companyBySiren(siren, false)).thenReturn(Future.successful(companySearchResults))
        when(companyRepository.findBySirets(companySearchResults.map(_.siret)))
          .thenReturn(Future.successful(List(existingCompany1, existingCompany2)))
        when(
          companyOrchestrator.getOrCreate(
            argMatching[CompanyCreation] { case cc: CompanyCreation if cc.siret == company3.siret => }
          )
        ).thenReturn(Future.successful(company3))
        when(userOrchestrator.list(users.map(_.email)))
          .thenReturn(Future.successful(List(existingUser1, existingUser2)))

        when(
          proAccessTokenOrchestrator.sendInvitations(
            List(existingCompany1, existingCompany2, company3),
            user3.email,
            AccessLevel.ADMIN
          )
        ).thenReturn(Future.unit)
        when(
          proAccessTokenOrchestrator.addInvitedUserAndNotify(
            existingUser1,
            List(existingCompany1, existingCompany2, company3),
            AccessLevel.ADMIN
          )
        ).thenReturn(Future.unit)
        when(
          proAccessTokenOrchestrator.addInvitedUserAndNotify(
            existingUser2,
            List(existingCompany1, existingCompany2, company3),
            AccessLevel.ADMIN
          )
        ).thenReturn(Future.unit)

        importOrchestrator
          .importUsers(Some(siren), List.empty, users.map(_.email), false, AccessLevel.ADMIN)
          .map(res => res shouldEqual ())
      }
    }
  }
}
