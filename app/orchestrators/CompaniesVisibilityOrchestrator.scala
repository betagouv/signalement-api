package orchestrators

import models.User
import models.UserRole
import models.company.AccessLevel
import models.company.Company
import models.company.CompanyAccess
import models.company.CompanyAccessKind
import models.company.CompanyWithAccess
import models.company.CompanyWithAccessAndCounts
import models.company.ProCompanies
import models.company.AccessLevel.ADMIN
import models.company.AccessLevel.MEMBER
import models.company.AccessLevel.NONE
import models.company.CompanyAccessKind.Direct
import models.company.CompanyAccessKind.Inherited
import models.company.CompanyAccessKind.InheritedAdminAndDirectMember
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.report.ReportRepositoryInterface
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
    companyAccessRepository: CompanyAccessRepositoryInterface,
    reportRepository: ReportRepositoryInterface
)(implicit val executionContext: ExecutionContext) {

  // Fetch all users of this company, and of its head office
  def fetchUsersWithHeadOffices(siret: SIRET): Future[List[User]] =
    for {
      companies <- companyRepo.findCompanyAndHeadOffice(siret)
      users     <- companyAccessRepository.fetchUsersByCompanies(companies.map(_.id))
    } yield users

  def fetchUsersByCompany(companyId: UUID): Future[List[User]] =
    companyAccessRepository.fetchUsersByCompanies(List(companyId))

  def fetchUsersOfCompanies(companies: List[(SIRET, UUID)]): Future[Map[UUID, List[User]]] =
    companyAccessRepository.fetchUsersByCompanyIds(companies.map(_._2))

  def fetchVisibleCompaniesExtended(pro: User) =
    for {
      proCompanies <- fetchVisibleCompanies(pro: User)
      allCompaniesIds = proCompanies.toSimpleList.map(_.company.id)
      reportsCounts        <- companyRepo.getReportsCounts(allCompaniesIds)
      ongoingReportsCounts <- reportRepository.countOngoingReportsByCompany(allCompaniesIds)
      usersByCompanyId <- fetchUsersOfCompanies(
        proCompanies.toSimpleList.map(c => c.company.siret -> c.company.id)
      )
      proCompaniesExtended = proCompanies.map { case CompanyWithAccess(company, access) =>
        val companyId           = company.id
        val reportsCount        = reportsCounts.getOrElse(companyId, 0L)
        val ongoingReportsCount = ongoingReportsCounts.getOrElse(companyId, 0)
        val usersCount          = usersByCompanyId.get(companyId).map(_.length).getOrElse(0)
        CompanyWithAccessAndCounts(
          company,
          access,
          reportsCount,
          ongoingReportsCount,
          usersCount
        )
      }
    } yield proCompaniesExtended

  def fetchVisibleCompanies(pro: User) =
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
      proCompaniesWithAllAccesses = addInheritedAccessesToSubsidiaries(
        proCompaniesWithAccesses,
        keepLegacyInheritanceAlgorithm = true
      )
    } yield proCompaniesWithAllAccesses

  def fetchVisibleCompaniesList(pro: User): Future[List[CompanyWithAccess]] =
    for {
      proCompaniesWithAccesses <- fetchVisibleCompanies(pro)
    } yield proCompaniesWithAccesses.toSimpleList

  private[this] def addInheritedAccessesToSubsidiaries(
      proCompaniesWithAccesses: ProCompanies[CompanyWithAccess],
      // There was weird edge cases in how the inheritance was coded previously
      // We have recoded everything but for now we maintain the odd behaviors
      keepLegacyInheritanceAlgorithm: Boolean
  ): ProCompanies[CompanyWithAccess] =
    proCompaniesWithAccesses.copy(
      headOfficesAndSubsidiaries = proCompaniesWithAccesses.headOfficesAndSubsidiaries.map {
        case (headOffice, subsidiaries) =>
          headOffice -> subsidiaries.map {

            case CompanyWithAccess(subsidiary, CompanyAccess(NONE, _)) =>
              val isAdminInAnyOfTheGroup = (headOffice +: subsidiaries).exists(_.isAdmin)

              val level =
                // This is a weird part of the old implementation, most likely unintended
                // If no direct access, and we are member of the head office, BUT we are admin of a least one sibling
                // Then we end up admin here...
                if (keepLegacyInheritanceAlgorithm && isAdminInAnyOfTheGroup) ADMIN
                else headOffice.access.level
              CompanyWithAccess(
                subsidiary,
                CompanyAccess(level = level, kind = Inherited)
              )
            case companyWithAccess @ CompanyWithAccess(subsidiary, CompanyAccess(MEMBER, Direct))
                if headOffice.access.level == ADMIN =>
              keepLegacyInheritanceAlgorithm match {
                case true =>
                  // In the old implementation, the direct member access takes precedence over what's in the head office
                  companyWithAccess
                case false =>
                  CompanyWithAccess(
                    subsidiary,
                    CompanyAccess(level = ADMIN, kind = InheritedAdminAndDirectMember)
                  )
              }
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
      toFill: ProCompanies[Company],
      companiesWithAccesses: List[CompanyWithAccess]
  ): ProCompanies[CompanyWithAccess] = {

    def withAccess(c: Company) =
      companiesWithAccesses
        .find(_.company.id == c.id)
        .getOrElse(CompanyWithAccess(c, CompanyAccess(level = AccessLevel.NONE, kind = CompanyAccessKind.Inherited)))

    ProCompanies(
      headOfficesAndSubsidiaries = toFill.headOfficesAndSubsidiaries.map { case (key, value) =>
        withAccess(key) -> value.map(withAccess)
      },
      loneSubsidiaries = toFill.loneSubsidiaries.map(withAccess)
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

object CompaniesVisibilityOrchestrator {

  sealed trait AccessLevelInheritanceAlgorithm

  object AccessLevelInheritanceAlgorithm {

    // The intuitive way, what we should have :
    // you inherit the from the head office, if it's better than the one you have
    case object Intuitive extends AccessLevelInheritanceAlgorithm

    // A weird implementation, it was like that before, we keep it this way for now
    // it behaves differently in two different edge cases
    case object KeepLegacyEdgeCases extends AccessLevelInheritanceAlgorithm

  }
}
