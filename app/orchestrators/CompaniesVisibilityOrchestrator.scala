package orchestrators

import models.User
import models.UserRole
import models.company.AccessLevel
import models.company.Company
import models.company.CompanyAccessKind
import models.company.CompanyWithAccess
import models.company.ProCompanies
import models.company.ProCompaniesWithAccesses
import models.company.AccessLevel.ADMIN
import models.company.AccessLevel.MEMBER
import models.company.AccessLevel.NONE
import models.company.CompanyAccessKind.Direct
import models.company.CompanyAccessKind.Synthetic
import models.company.CompanyAccessKind.SyntheticAdminAndDirectMember
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import utils.SIREN
import utils.SIRET

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class SiretsSirens(sirens: Seq[SIREN], sirets: Seq[SIRET]) {
  def toList() = sirens.map(_.value).concat(sirets.map(_.value)).distinct
}

class CompaniesVisibilityOrchestrator(
    companyRepo: CompanyRepositoryInterface,
    companyAccessRepository: CompanyAccessRepositoryInterface
)(implicit val executionContext: ExecutionContext) {

  // Fetch all users of this company, and of its head office
  def fetchUsersWithHeadOffices(siret: SIRET): Future[List[User]] =
    for {
      companies <- companyRepo.findCompanyAndHeadOffice(siret)
      users     <- companyAccessRepository.fetchUsersByCompanies(companies.map(_.id))
    } yield users

  def fetchUsersByCompany(companyId: UUID): Future[List[User]] =
    companyAccessRepository.fetchUsersByCompanies(List(companyId))

  // Fetch all users of these companies, and of their head offices
  // convoluted, could be simplified
  // Why do we bother with company id + siret ? could a report company_id + company_siret point at different companies ?
  // seems useless, especially since we're returning results with just the company_id
  def fetchUsersWithHeadOffices(companies: List[(SIRET, UUID)]): Future[Map[UUID, List[User]]] =
    for {
      usersByCompanyIdMap <- companyAccessRepository.fetchUsersByCompanyIds(companies.map(_._2))
      sirens = companies.map(c => SIREN.fromSIRET(c._1))
      headOfficesCompany <-
        companyRepo.findHeadOffices(sirens, openOnly = false)
      headOfficeUsersByHeadOfficesCompanyIdMap <- companyAccessRepository.fetchUsersByCompanyIds(
        headOfficesCompany.map(_.id)
      )
      headOfficeIdByCompanyIdMap: Map[UUID, Option[UUID]] = companies
        // there seems no reason to worry about uniqueness, both SIRET and id are unique
        .groupBy(_._2)
        .view
        .mapValues { uniqueSiretCompanyIdTuple =>
          // here we are computing SIREN again, but we did that above already
          val siren = uniqueSiretCompanyIdTuple.headOption.map(x => SIREN.fromSIRET(x._1))
          headOfficesCompany.find(c => siren.contains(SIREN.fromSIRET(c.siret))).map(_.id)
        }
        .toMap
    } yield usersByCompanyIdMap.map { case (companyId, usersOfCompany) =>
      // we could just find the head office here based on the SIREN, no need to bother with constructing the "headOfficeIdByCompanyIdMap" earlier
      val headOfficeId    = headOfficeIdByCompanyIdMap.get(companyId).flatten
      val headOfficeUsers = headOfficeId.flatMap(headOfficeUsersByHeadOfficesCompanyIdMap.get).getOrElse(List())
      (companyId, (usersOfCompany ++ headOfficeUsers).distinctBy(_.id))
    }

  def fetchVisibleCompaniesAsMap(pro: User) =
    for {
      companiesWithAccesses <- companyAccessRepository.fetchCompaniesWithLevel(pro)
      (headOffices, subsidiaries) =
        companiesWithAccesses.map(_.company).partition(_.isHeadOffice)
      headOfficesWithTheirSubsidiaries <- fetchSubsidiaries(headOffices)
      loneSubsidiaries = {
        val subsidiariesFromHeadOffices = headOfficesWithTheirSubsidiaries.values.flatten
        subsidiaries.filter(c => !subsidiariesFromHeadOffices.exists(_.id == c.id))
      }
      proCompanies = ProCompanies(headOfficesWithTheirSubsidiaries, loneSubsidiaries)
      proCompaniesWithAccesses = fillWithCorrespondingAccesses(
        proCompanies,
        companiesWithAccesses
      )
      proCompaniesWithAllAccesses = addSyntheticAccessesToSubsidiaries(
        proCompaniesWithAccesses
      )
      // TODO il faudra des TUs
    } yield proCompaniesWithAllAccesses

  private[this] def addSyntheticAccessesToSubsidiaries(
      proCompaniesWithAccesses: ProCompaniesWithAccesses
  ): ProCompaniesWithAccesses =
    proCompaniesWithAccesses.copy(
      headOfficesAndSubsidiaries =
        proCompaniesWithAccesses.headOfficesAndSubsidiaries.map { case (headOffice, subsidiaries) =>
          headOffice -> subsidiaries.map {
            case CompanyWithAccess(subsidiary, NONE, _) =>
              CompanyWithAccess(
                subsidiary,
                level = headOffice.level,
                kind = Synthetic
              )
            case CompanyWithAccess(subsidiary, MEMBER, Direct) if headOffice.level == ADMIN =>
              CompanyWithAccess(
                subsidiary,
                level = ADMIN,
                kind = SyntheticAdminAndDirectMember
              )
            case other => other
          }
        }
    )

  private[this] def fetchSubsidiaries(headOffices: List[Company]): Future[Map[Company, List[Company]]] =
    for {
      allCompanies <- companyRepo.findBySiren(headOffices.map(_.siren))
      mapWithSubsidiaries = headOffices.map { headOffice =>
        val subsidiaries = allCompanies
          .filter(_.siren == headOffice.siren)
          .filter(_.siret != headOffice.siret)
          .filter(!_.isHeadOffice)
        headOffice -> subsidiaries
      }.toMap
    } yield mapWithSubsidiaries

  private[this] def fillWithCorrespondingAccesses(
      toFill: ProCompanies,
      companiesWithAccesses: List[CompanyWithAccess]
  ): ProCompaniesWithAccesses = {

    def withAccess(c: Company) =
      companiesWithAccesses
        .find(_.company.id == c.id)
        .getOrElse(CompanyWithAccess.none(c))

    ProCompaniesWithAccesses(
      headOfficesAndSubsidiaries = toFill.headOfficesAndSubsidiaries.map { case (key, value) =>
        withAccess(key) -> value.map(withAccess)
      },
      loneSubsidiaries = toFill.loneSubsidiaries.map(withAccess)
    )

  }

  // @@@@@ The legacy function
  // TODO brancher sur ma nouvelle méthode.
  // TODO TUs d'abord ?
  def fetchVisibleCompanies(pro: User): Future[List[CompanyWithAccess]] =
    for {
      authorizedCompanies <- companyAccessRepository.fetchCompaniesWithLevel(pro)
      headOfficeSirets = authorizedCompanies.filter(_.company.isHeadOffice).map(_.company.siret)
      companiesForHeadOffices <- companyRepo.findBySiren(headOfficeSirets.map(SIREN.fromSIRET))
      companiesForHeadOfficesWithAccesses = addAccessToSubsidiaries(authorizedCompanies, companiesForHeadOffices)
      accessiblesCompanies = (authorizedCompanies ++ companiesForHeadOfficesWithAccesses)
        .distinctBy(_.company.siret)
        .sortBy(_.company.siret.value)
    } yield accessiblesCompanies

  private[this] def addAccessToSubsidiaries(
      authorizedCompaniesWithAccesses: List[CompanyWithAccess],
      accessibleSubsidiaries: List[Company]
  ) = {
    val levelPriority = Map(
      AccessLevel.ADMIN  -> 1,
      AccessLevel.MEMBER -> 0
    ).withDefaultValue(-1)
    val getLevelBySiren = authorizedCompaniesWithAccesses
      .groupMapReduce(c => SIREN.fromSIRET(c.company.siret))(_.level)((a, b) =>
        if (levelPriority(a) > levelPriority(b)) a else b
      )
      .withDefaultValue(AccessLevel.NONE)
    accessibleSubsidiaries.map(c =>
      CompanyWithAccess(
        c,
        getLevelBySiren(SIREN.fromSIRET(c.siret)),

        // @@@ this is false, but we should erase this function in the end
        kind = CompanyAccessKind.Direct
      )
    )
  }

  private[this] def fetchVisibleSiretsSirens(user: User): Future[SiretsSirens] =
    for {
      companyWithAccessList <- companyAccessRepository.fetchCompaniesWithLevel(user)
      authorizedSirets = companyWithAccessList.map(_.company.siret)
      authorizedHeadofficeSirens <-
        companyRepo
          .findBySirets(authorizedSirets)
          .map(companies =>
            companies
              .filter(_.isHeadOffice)
              .map(c => SIREN.fromSIRET(c.siret))
          )
    } yield removeRedundantSirets(SiretsSirens(authorizedHeadofficeSirens, authorizedSirets))

  def filterUnauthorizedSiretSirenList(siretSirenList: Seq[String], user: User): Future[Seq[String]] =
    if (user.userRole == UserRole.Professionnel) {
      val formattedSiretsSirens = formatSiretSirenList(siretSirenList)
      fetchVisibleSiretsSirens(user).map { allowed =>
        val filteredSiretsSirens = SiretsSirens(
          sirets = formattedSiretsSirens.sirets.filter(wanted =>
            allowed.sirens.contains(SIREN.fromSIRET(wanted)) || allowed.sirets.contains(wanted)
          ),
          sirens = allowed.sirens.intersect(formattedSiretsSirens.sirens)
        ).toList()
        if (filteredSiretsSirens.isEmpty) {
          allowed.toList()
        } else {
          filteredSiretsSirens
        }
      }
    } else {
      Future.successful(siretSirenList)
    }

  private[this] def removeRedundantSirets(id: SiretsSirens): SiretsSirens =
    SiretsSirens(
      id.sirens,
      id.sirets.filter(siret => !id.sirens.contains(SIREN.fromSIRET(siret)))
    )

  private[this] def formatSiretSirenList(siretSirenList: Seq[String]): SiretsSirens =
    SiretsSirens(
      sirens = siretSirenList.filter(SIREN.isValid).map(SIREN.fromUnsafe),
      sirets = siretSirenList.filter(SIRET.isValid).map(SIRET.apply)
    )

}
