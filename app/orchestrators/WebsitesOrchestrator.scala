package orchestrators

import cats.implicits.catsSyntaxOption
import controllers.error.AppError.CannotDeleteWebsite
import controllers.error.AppError.MalformedHost
import controllers.error.AppError.WebsiteNotFound
import models.Company
import models.CompanyCreation
import models.PaginatedResult
import models.investigation.InvestigationStatus.NotProcessed
import models.website.WebsiteCompanyReportCount.toApi
import models.website._
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.website.WebsiteRepositoryInterface
import repositories.websiteinvestigation.WebsiteInvestigationRepositoryInterface
import utils.Country
import utils.URL

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class WebsitesOrchestrator(
    val repository: WebsiteRepositoryInterface,
    val websiteInvestigationRepository: WebsiteInvestigationRepositoryInterface,
    val companyRepository: CompanyRepositoryInterface
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  def searchByHost(host: String): Future[Seq[Country]] =
    for {
      validHost <- URL(host).getHost.liftTo[Future](MalformedHost(host))
      websites <- repository.searchValidWebsiteAssociationByHost(validHost)
    } yield websites
      .flatMap(_.companyCountry)
      .map(Country.fromName)

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

  def updateWebsiteKind(websiteId: WebsiteId, kind: WebsiteKind): Future[Website] = for {
    website <- findWebsite(websiteId)
    _ = logger.debug(s"Updating website kind to ${kind}")
    updatedWebsite = website.copy(kind = kind)
    _ <- repository.update(updatedWebsite.id, updatedWebsite)
    _ <-
      if (kind == WebsiteKind.DEFAULT) {
        logger.debug(s"Removing other websites with the same host : ${website.host}")
        repository
          .removeOtherWebsitesWithSameHost(website)
      } else Future.successful(())
  } yield updatedWebsite

  def updateCompany(websiteId: WebsiteId, companyToAssign: CompanyCreation): Future[WebsiteAndCompany] = for {
    company <- {
      logger.debug(s"Updating website id ${websiteId} with company siret : ${companyToAssign.siret}")
      getOrCreateCompay(companyToAssign)
    }
    website <- findWebsite(websiteId)
    websiteToUpdate = website.copy(companyCountry = None, companyId = Some(company.id), kind = WebsiteKind.DEFAULT)
    _ = logger.debug(s"Website to update : ${websiteToUpdate}")
    updatedWebsite <- repository.update(websiteToUpdate.id, websiteToUpdate)
    _ = logger.debug(s"Removing other websites with the same host : ${website.host}")
    _ <- repository
      .removeOtherWebsitesWithSameHost(website)
    _ = logger.debug(s"Website company successfully updated")
  } yield WebsiteAndCompany.toApi(updatedWebsite, Some(company))

  def updateCompanyCountry(websiteId: WebsiteId, companyCountry: String): Future[WebsiteAndCompany] = for {
    website <- {
      logger.debug(s"Updating website id ${websiteId.value} with company country : ${companyCountry}")
      findWebsite(websiteId)
    }
    websiteToUpdate = website.copy(
      companyCountry = Some(companyCountry),
      companyId = None,
      kind = WebsiteKind.DEFAULT
    )
    _ = logger.debug(s"Website to update : ${websiteToUpdate}")
    updatedWebsite <- repository.update(websiteToUpdate.id, websiteToUpdate)
    _ = logger.debug(s"Removing other websites with the same host : ${website.host}")
    _ <- repository
      .removeOtherWebsitesWithSameHost(website)
    _ = logger.debug(s"Website company country successfully updated")
  } yield WebsiteAndCompany.toApi(updatedWebsite, maybeCompany = None)

  def delete(websiteId: WebsiteId): Future[Unit] =
    for {
      maybeWebsite <- repository.get(websiteId)
      website <- maybeWebsite.liftTo[Future](WebsiteNotFound(websiteId))
      maybeInvestigation <- websiteInvestigationRepository.get(websiteId)
      _ <-
        if (website.kind == WebsiteKind.DEFAULT) {
          logger.debug(s"Cannot delete identified website")
          Future.failed(CannotDeleteWebsite(website.host))
        } else {
          Future.unit
        }
      _ <- maybeInvestigation match {
        case Some(i) if i.attribution.isEmpty && i.investigationStatus == NotProcessed =>
          websiteInvestigationRepository.delete(i.id)
        case Some(_) =>
          logger.debug(s"Cannot delete website under investigation")
          Future.failed(CannotDeleteWebsite(website.host))
        case None => Future.unit
      }
      _ <- repository
        .delete(websiteId)
    } yield ()

  private[this] def getOrCreateCompay(companyCreate: CompanyCreation): Future[Company] = companyRepository
    .getOrCreate(
      companyCreate.siret,
      Company(
        siret = companyCreate.siret,
        name = companyCreate.name,
        address = companyCreate.address,
        activityCode = companyCreate.activityCode
      )
    )

  private[this] def findWebsite(websiteId: WebsiteId): Future[Website] = for {
    maybeWebsite <- {
      logger.debug(s"Searching for website with id : $websiteId")
      repository.get(websiteId)
    }
    website <- maybeWebsite.liftTo[Future](WebsiteNotFound(websiteId))
    _ = logger.debug(s"Found website")
  } yield website

}
