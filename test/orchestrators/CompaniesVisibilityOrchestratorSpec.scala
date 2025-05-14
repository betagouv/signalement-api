package orchestrators

import models.company.AccessLevel
import models.company.Company
import models.company.CompanyAccess
import models.company.CompanyAccessKind
import models.company.CompanyWithAccess
import models.company.ProCompanies
import models.company.AccessLevel.ADMIN
import models.company.AccessLevel.MEMBER
import models.company.CompanyAccessKind.Direct
import org.mockito.Mockito.when
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.report.ReportRepository
import utils.AppSpec
import utils.Fixtures
import utils.SIREN
import utils.SIRET

import scala.concurrent.Future

class CompaniesVisibilityOrchestratorSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers {

  "CompaniesVisibilityOrchestrator" should {
    "fetchVisibleCompanies" should {
      "work as expected" in {

        val proUser = Fixtures.genProUser.sample.get

        val (
          superCorp,
          superCorpSubsidiary1,
          superCorpSubsidiary2,
          superCorpSubsidiary3
        ) =
          genHeadOfficeAndThreeSubsidiaries(siren = "1" * 9)
        val (
          maxiCorp,
          maxiCorpSubsidiary1,
          maxiCorpSubsidiary2,
          maxiCorpSubsidiary3
        ) = genHeadOfficeAndThreeSubsidiaries(siren = "2" * 9)
        val zetaCorpSubsidiary        = genLoneSubsidiary("3" * 9)
        val deltaCorpSubsidiary       = genLoneSubsidiary("4" * 9)
        val inaccessibleUnrelatedCorp = genLoneSubsidiary("5" * 9)

        val whatsInDb = List(
          // Supercorp
          superCorp            -> Some(ADMIN),
          superCorpSubsidiary1 -> Some(ADMIN),
          superCorpSubsidiary2 -> Some(MEMBER),
          superCorpSubsidiary3 -> None,
          // Maxicorp: same, except he's member on the head office, not admin
          maxiCorp            -> Some(MEMBER),
          maxiCorpSubsidiary1 -> Some(ADMIN),
          maxiCorpSubsidiary2 -> Some(MEMBER),
          maxiCorpSubsidiary3 -> None,
          // Lone subsidiaries:
          zetaCorpSubsidiary  -> Some(ADMIN),
          deltaCorpSubsidiary -> Some(MEMBER),
          // This one is not related siren-wise and he also has no access to it
          inaccessibleUnrelatedCorp -> None
        )
        val accessesInDb = whatsInDb.collect { case (company, Some(level)) =>
          CompanyWithAccess(company, CompanyAccess(level, Direct))
        }
        val allCompaniesInDb = whatsInDb.map(_._1)

        val companyRepository       = mock[CompanyRepositoryInterface]
        val companyAccessRepository = mock[CompanyAccessRepositoryInterface]
        val reportRepository        = mock[ReportRepository]
        val companiesVisibilityOrchestrator = new CompaniesVisibilityOrchestrator(
          companyRepository,
          companyAccessRepository,
          reportRepository
        )

        when(companyAccessRepository.fetchCompaniesWithLevel(proUser))
          .thenReturn(Future.successful(accessesInDb))

        when(companyRepository.findBySiren(anyListOf[SIREN]))
          .thenReturn(Future.successful(allCompaniesInDb.filter(_ != inaccessibleUnrelatedCorp)))

        val expectedResult = ProCompanies[CompanyWithAccess](
          headOfficesAndSubsidiaries = Map(
            cWA(superCorp, ADMIN, Direct) -> List(
              cWA(superCorpSubsidiary1, ADMIN, Direct),
              cWA(superCorpSubsidiary2, MEMBER, Direct)
            ),
            cWA(maxiCorp, MEMBER, Direct) -> List(
              cWA(maxiCorpSubsidiary1, ADMIN, Direct),
              cWA(maxiCorpSubsidiary2, MEMBER, Direct)
            )
          ),
          loneSubsidiaries = List(
            cWA(zetaCorpSubsidiary, ADMIN, Direct),
            cWA(deltaCorpSubsidiary, MEMBER, Direct)
          )
        )

        for {
          res <- companiesVisibilityOrchestrator.fetchVisibleCompanies(proUser)
        } yield res shouldEqual expectedResult

      }
    }

  }

  private def genHeadOfficeAndThreeSubsidiaries(siren: String): (Company, Company, Company, Company) = {
    val headOffice = Fixtures.genCompany.sample.get.copy(isHeadOffice = true, siret = SIRET(s"${siren}00000"))
    def subsidiary(n: Int) =
      Fixtures.genCompany.sample.get.copy(isHeadOffice = false, siret = SIRET(s"${siren}0000${n}"))
    (headOffice, subsidiary(1), subsidiary(2), subsidiary(3))
  }

  private def genLoneSubsidiary(siren: String): Company =
    Fixtures.genCompany.sample.get.copy(isHeadOffice = false, siret = SIRET(s"${siren}00001"))

  private def cWA(company: Company, level: AccessLevel, kind: CompanyAccessKind) =
    CompanyWithAccess(company, CompanyAccess(level, kind))
}
