package orchestrators

import models.company.Company
import models.company.CompanyWithAccess
import models.company.ProCompaniesWithAccesses
import models.company.AccessLevel.ADMIN
import models.company.AccessLevel.MEMBER
import models.company.CompanyAccessKind.Direct
import models.company.CompanyAccessKind.SyntheticAdminAndDirectMember
import models.company.CompanyAccessKind.Synthetic
import org.mockito.Mockito.when
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
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
          CompanyWithAccess(company, level, Direct)
        }
        val allCompaniesInDb = whatsInDb.map(_._1)

        val companyRepository       = mock[CompanyRepositoryInterface]
        val companyAccessRepository = mock[CompanyAccessRepositoryInterface]
        val companiesVisibilityOrchestrator = new CompaniesVisibilityOrchestrator(
          companyRepository,
          companyAccessRepository
        )

        when(companyAccessRepository.fetchCompaniesWithLevel(proUser))
          .thenReturn(Future.successful(accessesInDb))

        when(companyRepository.findBySiren(anyListOf[SIREN]))
          .thenReturn(Future.successful(allCompaniesInDb.filter(_ != inaccessibleUnrelatedCorp)))

        val expectedResult = ProCompaniesWithAccesses(
          headOfficesAndSubsidiaries = Map(
            CompanyWithAccess(superCorp, ADMIN, Direct) -> List(
              CompanyWithAccess(superCorpSubsidiary1, ADMIN, Direct),
              CompanyWithAccess(superCorpSubsidiary2, ADMIN, SyntheticAdminAndDirectMember),
              CompanyWithAccess(superCorpSubsidiary3, ADMIN, Synthetic)
            ),
            CompanyWithAccess(maxiCorp, MEMBER, Direct) -> List(
              CompanyWithAccess(maxiCorpSubsidiary1, ADMIN, Direct),
              CompanyWithAccess(maxiCorpSubsidiary2, MEMBER, Direct),
              CompanyWithAccess(maxiCorpSubsidiary3, MEMBER, Synthetic)
            )
          ),
          loneSubsidiaries = List(
            CompanyWithAccess(zetaCorpSubsidiary, ADMIN, Direct),
            CompanyWithAccess(deltaCorpSubsidiary, MEMBER, Direct)
          )
        )

        for {
          res <- companiesVisibilityOrchestrator.fetchVisibleCompanies(proUser)
        } yield res shouldEqual expectedResult

      }
    }

    // TODO supprimer ce TU plus tard
    "the old version" should {
      "work like that" in {

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
          CompanyWithAccess(company, level, Direct)
        }
        val allCompaniesInDb = whatsInDb.map(_._1)

        val companyRepository       = mock[CompanyRepositoryInterface]
        val companyAccessRepository = mock[CompanyAccessRepositoryInterface]
        val companiesVisibilityOrchestrator = new CompaniesVisibilityOrchestrator(
          companyRepository,
          companyAccessRepository
        )

        when(companyAccessRepository.fetchCompaniesWithLevel(proUser))
          .thenReturn(Future.successful(accessesInDb))

        when(companyRepository.findBySiren(anyListOf[SIREN]))
          .thenReturn(Future.successful(allCompaniesInDb.filter(_ != inaccessibleUnrelatedCorp)))

        val expectedList = List(
          CompanyWithAccess(superCorp, ADMIN, Direct),
          CompanyWithAccess(superCorpSubsidiary1, ADMIN, Direct),
          CompanyWithAccess(superCorpSubsidiary2, MEMBER, Direct), // WTF ? d'après moi ça devrait être ADMIN
          CompanyWithAccess(superCorpSubsidiary3, ADMIN, Direct),
          CompanyWithAccess(maxiCorp, MEMBER, Direct),
          CompanyWithAccess(maxiCorpSubsidiary1, ADMIN, Direct),
          CompanyWithAccess(maxiCorpSubsidiary2, MEMBER, Direct),
          CompanyWithAccess(maxiCorpSubsidiary3, ADMIN, Direct), // WTF ? d'après moi ça devrait être MEMBER
          CompanyWithAccess(zetaCorpSubsidiary, ADMIN, Direct),
          CompanyWithAccess(deltaCorpSubsidiary, MEMBER, Direct)
        )

        for {
          res <- companiesVisibilityOrchestrator.fetchVisibleCompaniesLegacy(proUser)
        } yield res.sortBy(_.company.siret.value) shouldEqual expectedList.sortBy(_.company.siret.value)

      }
    }
  }

  def genHeadOfficeAndThreeSubsidiaries(siren: String): (Company, Company, Company, Company) = {
    val headOffice = Fixtures.genCompany.sample.get.copy(isHeadOffice = true, siret = SIRET(s"${siren}00000"))
    def subsidiary(n: Int) =
      Fixtures.genCompany.sample.get.copy(isHeadOffice = false, siret = SIRET(s"${siren}0000${n}"))
    (headOffice, subsidiary(1), subsidiary(2), subsidiary(3))
  }

  def genLoneSubsidiary(siren: String): Company =
    Fixtures.genCompany.sample.get.copy(isHeadOffice = false, siret = SIRET(s"${siren}00001"))

}
