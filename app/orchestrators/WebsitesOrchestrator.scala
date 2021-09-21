package orchestrators

import cats.implicits.catsSyntaxOption
import controllers.error.AppError.CompanyAlreadyAssociatedToWebsite
import controllers.error.AppError.WebsiteNotFound
import models.Company
import models.CompanyCreation
import models.PaginatedResult
import models.website.WebsiteCompanyReportCount.toApi
import models.website._
import play.api.Logger
import repositories.CompanyRepository
import repositories.WebsiteRepository

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class WebsitesOrchestrator @Inject() (
    val repository: WebsiteRepository,
    val companyRepository: CompanyRepository
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  def getWebsiteCompanyCount(
      maybeHost: Option[String],
      kinds: Option[Seq[WebsiteKind]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[WebsiteCompanyReportCount]] =
    for {
      websites <- repository.listWebsitesCompaniesByReportCount(maybeHost, kinds, maybeOffset, maybeLimit)
      _ = logger.debug("Website company report fetched")
      websitesWithCount = websites.copy(entities = websites.entities.map(toApi))
    } yield websitesWithCount

  def updateWebsiteKind(websiteId: UUID, kind: WebsiteKind): Future[Website] = for {
    website <- findWebsite(websiteId)
    _ <-
      if (kind == WebsiteKind.DEFAULT) {
        logger.debug(s"Unvalidate other websites with host : ${website.host}")
        unvalidateOtherWebsites(website)
      } else Future.successful(())
    _ = logger.debug(s"Updating website kind to ${kind}")
    updatedWebsite = website.copy(kind = kind)
    _ <- repository.update(updatedWebsite)
  } yield updatedWebsite

  def updateCompany(websiteId: UUID, companyToAssign: CompanyCreation): Future[WebsiteAndCompany] = for {
    company <- {
      logger.debug(s"Updating website id ${websiteId} with company siret : ${companyToAssign.siret}")
      getOrCreateCompay(companyToAssign)
    }
    website <- findWebsite(websiteId)
    _ = logger.debug(s"Validating company update")
    _ <- validateWebsiteAssociation(website, company)
    websiteToUpdate = website.copy(companyCountry = None, companyId = Some(company.id), kind = WebsiteKind.DEFAULT)
    _ = logger.debug(s"Website to update : ${websiteToUpdate}")
    updatedWebsite <- repository.update(websiteToUpdate)
    _ = logger.debug(s"Website company successfully updated")
  } yield WebsiteAndCompany.toApi(updatedWebsite, Some(company))

  private[this] def validateWebsiteAssociation(website: Website, companyToUpdate: Company) =
    for {
      otherAssociatedCompanies <- repository.searchCompaniesByHost(website.host)
      otherAssociatedCompaniesSiret = otherAssociatedCompanies.map(_._2.siret)
      _ <-
        if (otherAssociatedCompaniesSiret.contains(companyToUpdate.siret)) {
          logger.warn(s"Company already associated with website")
          Future.failed(CompanyAlreadyAssociatedToWebsite(website.id, companyToUpdate.siret))
        } else {
          logger.debug(s"Validation OK")
          Future.successful(())
        }
    } yield ()

  private[this] def unvalidateOtherWebsites(updatedWebsite: Website) =
    for {
      websitesWithSameHost <- repository
        .searchCompaniesByHost(updatedWebsite.host)
        .map(websites =>
          websites
            .map(_._1)
            .filter(_.id != updatedWebsite.id)
            .filter(_.kind == WebsiteKind.DEFAULT)
        )
      unvalidatedWebsites: Seq[Website] <-
        Future.sequence(
          websitesWithSameHost.map(website => repository.update(website.copy(kind = WebsiteKind.PENDING)))
        )
    } yield unvalidatedWebsites

  private[this] def getOrCreateCompay(companyCreate: CompanyCreation) = companyRepository
    .getOrCreate(
      companyCreate.siret,
      Company(
        siret = companyCreate.siret,
        name = companyCreate.name,
        address = companyCreate.address,
        activityCode = companyCreate.activityCode
      )
    )

  private[this] def findWebsite(websiteId: UUID): Future[Website] = for {
    maybeWebsite <- {
      logger.debug(s"Searching for website with id : $websiteId")
      repository.find(websiteId)
    }
    website <- maybeWebsite.liftTo[Future](WebsiteNotFound(websiteId))
    _ = logger.debug(s"Found website")
  } yield website

}
