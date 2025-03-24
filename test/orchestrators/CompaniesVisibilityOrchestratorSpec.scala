package orchestrators

import models.company.AccessLevel
import models.company.Company
import models.company.CompanyWithAccess
import models.company.ProCompaniesWithAccesses
import models.company.AccessLevel.ADMIN
import models.company.AccessLevel.MEMBER
import models.company.CompanyAccessKind.Direct
import models.company.CompanyAccessKind.SyntheticAdminAndDirectMember
import models.company.CompanyAccessKind.Synthetic
import org.specs2.concurrent.ExecutionEnv
import org.specs2.control.eff.Member
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import utils.AppSpec
import utils.Fixtures
import utils.SIRET

class CompaniesVisibilityOrchestratorSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers {

  "CompaniesVisibilityOrchestratorSpec" should {
    "fetchVisibleCompanies" should {
      "work as expected" in {

        val companyRepository       = mock[CompanyRepositoryInterface]
        val companyAccessRepository = mock[CompanyAccessRepositoryInterface]

        val companiesVisibilityOrchestrator = new CompaniesVisibilityOrchestrator(
          companyRepository,
          companyAccessRepository
        )

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
        val zetaCorpSubsidiary  = genLoneSubsidiary("3" * 9)
        val deltaCorpSubsidiary = genLoneSubsidiary("4" * 9)

        val accessesInDb = List(
          // Supercorp
          // note: no direct access on subsidiary3
          superCorp            -> ADMIN,
          superCorpSubsidiary1 -> ADMIN,
          superCorpSubsidiary2 -> MEMBER,
          // Maxicorp: same, except he's member on the head office, not admin
          maxiCorp             -> MEMBER,
          superCorpSubsidiary1 -> ADMIN,
          superCorpSubsidiary2 -> MEMBER,
          // Lone subsidiaries:
          zetaCorpSubsidiary  -> ADMIN,
          deltaCorpSubsidiary -> MEMBER
        )

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

        Fixtures.genCompany.sample.get.copy(isHeadOffice = true)

//        val

        for {
          result <- companiesVisibilityOrchestrator.fetchVisibleCompaniesLegacy(proUser)
        } yield result shouldEqual 3 + 3

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
