package orchestrators

import models.company.AccessLevel
import models.company.Company
import repositories.company.CompanyRepositoryInterface
import tasks.company.CompanySearchResult
import utils.EmailAddress
import utils.SIREN
import utils.SIRET

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait ImportOrchestratorInterface {
  def importUsers(
      siren: Option[SIREN],
      sirets: List[SIRET],
      emails: List[EmailAddress],
      onlyHeadOffice: Boolean,
      level: AccessLevel
  ): Future[Unit]
}

class ImportOrchestrator(
    companyRepository: CompanyRepositoryInterface,
    userOrchestrator: UserOrchestratorInterface,
    proAccessTokenOrchestrator: ProAccessTokenOrchestratorInterface
)(implicit ec: ExecutionContext)
    extends ImportOrchestratorInterface {

  private[orchestrators] def toCompany(c: CompanySearchResult) =
    Company(
      siret = c.siret,
      name = c.name.getOrElse(""),
      address = c.address,
      activityCode = c.activityCode,
      isHeadOffice = c.isHeadOffice,
      isOpen = c.isOpen,
      isPublic = c.isPublic,
      brand = c.brand
    )

  def importUsers(
      siren: Option[SIREN],
      sirets: List[SIRET],
      emails: List[EmailAddress],
      onlyHeadOffice: Boolean,
      level: AccessLevel
  ): Future[Unit] = {
    val foobar: List[CompanySearchResult] = List.empty
    for {
      existingCompanies <- companyRepository.findBySirets(sirets)
      _ = sirets.diff(existingCompanies.map(_.siret))
      missingCompanies <- Future.successful(foobar)
      createdCompanies <- Future.sequence(
        missingCompanies.map(c => companyRepository.getOrCreate(c.siret, toCompany(c)))
      )

      companiesFromSiren <- siren match {
        case Some(_) => Future.successful(foobar)
        case None    => Future.successful(List.empty)
      }
      existingCompaniesFromSiren <- companyRepository.findBySirets(companiesFromSiren.map(_.siret))
      missingCompaniesFromSiren = companiesFromSiren.filter(c => existingCompaniesFromSiren.forall(_.siret != c.siret))
      createdCompaniesFromSiren <- Future.sequence(
        missingCompaniesFromSiren.map(c => companyRepository.getOrCreate(c.siret, toCompany(c)))
      )

      existingUsers <- userOrchestrator.list(emails)
      missingUsers = emails.diff(existingUsers.map(_.email))
      allCompanies = existingCompanies ++ createdCompanies ++ existingCompaniesFromSiren ++ createdCompaniesFromSiren
      _ <- Future.sequence(
        missingUsers.map(email => proAccessTokenOrchestrator.sendInvitations(allCompanies, email, level))
      )
      _ <- Future.sequence(
        existingUsers.map(user => proAccessTokenOrchestrator.addInvitedUserAndNotify(user, allCompanies, level))
      )
    } yield ()
  }

}
