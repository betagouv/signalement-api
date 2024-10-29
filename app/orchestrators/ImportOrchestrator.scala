package orchestrators

import cats.implicits.toTraverseOps
import controllers.error.AppError.CreateWebsiteError
import models.User
import models.company.AccessLevel
import models.company.Company
import models.website.IdentificationStatus.Identified
import models.website.IdentificationStatus
import models.website.Website
import repositories.company.CompanyRepositoryInterface
import repositories.website.WebsiteRepositoryInterface
import tasks.company.CompanySearchResult
import tasks.company.CompanySyncServiceInterface
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

  def importMarketplaces(websites: List[(SIRET, String)], user: User): Future[List[Unit]]
}

class ImportOrchestrator(
    companyRepository: CompanyRepositoryInterface,
    companySyncService: CompanySyncServiceInterface,
    userOrchestrator: UserOrchestratorInterface,
    proAccessTokenOrchestrator: ProAccessTokenOrchestratorInterface,
    websiteRepository: WebsiteRepositoryInterface,
    websitesOrchestrator: WebsitesOrchestrator
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
      brand = c.brand,
      commercialName = c.commercialName,
      establishmentCommercialName = c.establishmentCommercialName
    )

  def importUsers(
      siren: Option[SIREN],
      sirets: List[SIRET],
      emails: List[EmailAddress],
      onlyHeadOffice: Boolean,
      level: AccessLevel
  ): Future[Unit] =
    for {
      existingCompanies <- companyRepository.findBySirets(sirets)
      missingSirets = sirets.diff(existingCompanies.map(_.siret))
      missingCompanies <- companySyncService.companiesBySirets(missingSirets)
      createdCompanies <- Future.sequence(
        missingCompanies.map(c => companyRepository.getOrCreate(c.siret, toCompany(c)))
      )

      companiesFromSiren <- siren match {
        case Some(siren) => companySyncService.companyBySiren(siren, onlyHeadOffice)
        case None        => Future.successful(List.empty)
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

  def importMarketplaces(websites: List[(SIRET, String)], user: User): Future[List[Unit]] = {

    val sirets = websites.map(_._1).distinct
    for {
      existingCompanies <- companyRepository.findBySirets(sirets)
      missingSirets = sirets.diff(existingCompanies.map(_.siret))
      missingCompanies <- companySyncService.companiesBySirets(missingSirets)
      createdCompanies <- missingCompanies.traverse(c => companyRepository.getOrCreate(c.siret, toCompany(c)))

      allCompanies         = existingCompanies ++ createdCompanies
      companiesAndWebsites = websites.map { case (siret, host) => allCompanies.find(_.siret == siret).get -> host }

      res <- companiesAndWebsites.traverse { case (company, host) =>
        for {
          websites <- websiteRepository.listByHost(host)
          _ <- websites.toList match {
            case website :: _ =>
              website.companyId match {
                case Some(_) if website.identificationStatus == Identified =>
                  val websiteToUpdate = website.copy(
                    companyId = Some(company.id),
                    isMarketplace = true
                  )
                  websiteRepository.update(websiteToUpdate.id, websiteToUpdate)
                case _ =>
                  for {
                    identified <- websiteRepository.listIdentified(website.host)
                    _ <-
                      if (identified.length > 1 && identified.flatMap(_.companyId).contains(company.id)) {
                        Future.failed(
                          CreateWebsiteError(
                            "L'entreprise ne peut pas être modifiée si plus d'un siret est associé au site web"
                          )
                        )
                      } else Future.unit
                    websiteToUpdate = website.copy(
                      companyCountry = None,
                      companyId = Some(company.id),
                      isMarketplace = true
                    )
                    updatedWebsite <- websitesOrchestrator.updateIdentification(websiteToUpdate, user)
                    _ <- websitesOrchestrator.updatePreviousReportsAssociatedToWebsite(website.host, company, user.id)
                  } yield updatedWebsite
              }
            case Nil =>
              val website = Website(
                host = host,
                companyCountry = None,
                companyId = Some(company.id),
                identificationStatus = IdentificationStatus.Identified,
                isMarketplace = true
              )
              for {
                createdWebsite <- websiteRepository.create(website)
                _ <- websitesOrchestrator.updatePreviousReportsAssociatedToWebsite(website.host, company, user.id)
              } yield createdWebsite

          }

        } yield ()
      }

    } yield res

  }
}
