package orchestrators

import models.User
import models.UserRole
import models.company._
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

  def fetchUsersOfCompany(siret: SIRET): Future[List[User]] =
    for {
      maybeCompany <- companyRepo.findBySiret(siret)
      users <- maybeCompany match {
        case Some(company) => companyAccessRepository.fetchUsersByCompanies(List(company.id))
        case None          => Future.successful(Nil)
      }
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
      proCompanies = organizeProCompanies(companiesWithAccesses.map(_.company))
      proCompaniesWithAccesses = fillWithCorrespondingAccesses(
        proCompanies,
        companiesWithAccesses
      )
    } yield proCompaniesWithAccesses

  private def organizeProCompanies(companies: List[Company]): ProCompanies[Company] = {
    val (headOffices, subsidiaries) = companies.partition(_.isHeadOffice)
    val headOfficesWithSubsidiaries = headOffices.map { headOffice =>
      val subsidiariesOfThisHeadOffice = subsidiaries.filter(_.siren == headOffice.siren)
      headOffice -> subsidiariesOfThisHeadOffice
    }.toMap
    val loneSubsidiaries = {
      val subsidiariesFromHeadOffices = headOfficesWithSubsidiaries.values.flatten
      subsidiaries.filter(c => !subsidiariesFromHeadOffices.exists(_.id == c.id))
    }
    ProCompanies(
      headOfficesWithSubsidiaries,
      loneSubsidiaries
    )
  }

  def fetchVisibleCompaniesList(pro: User): Future[List[CompanyWithAccess]] =
    for {
      proCompaniesWithAccesses <- fetchVisibleCompanies(pro)
    } yield proCompaniesWithAccesses.toSimpleList

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
      // Not sure of this logic, now that we don't have accesses inheritance.
      // We shouldn't allow to search on the whole SIRENs of the head offices
      // but then does it mean that we wouldn't be able to search by SIREN at all ? to investigate
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
