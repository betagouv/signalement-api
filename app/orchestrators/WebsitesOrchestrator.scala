package orchestrators

import cats.implicits.catsSyntaxMonadError
import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import controllers.error.AppError.CannotDeleteWebsite
import controllers.error.AppError.CreateWebsiteError
import controllers.error.AppError.MalformedHost
import controllers.error.AppError.WebsiteHostIsAlreadyIdentified
import controllers.error.AppError.WebsiteNotFound
import controllers.error.AppError.WebsiteNotIdentified
import models.PaginatedResult
import models.User
import models.company.Company
import models.company.CompanyCreation
import models.investigation.InvestigationStatus.NotProcessed
import models.investigation.InvestigationStatus
import models.investigation.WebsiteInvestigationApi
import models.report.ReportCompany
import models.website.IdentificationStatus._
import models.website.WebsiteCompanyReportCount.toApi
import models.website._
import play.api.Logger
import repositories.company.CompanyRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.website.WebsiteRepositoryInterface
import utils.Country
import utils.DateUtils
import utils.URL

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class WebsitesOrchestrator(
    val repository: WebsiteRepositoryInterface,
    val companyRepository: CompanyRepositoryInterface,
    val reportRepository: ReportRepositoryInterface,
    val reportOrchestrator: ReportOrchestrator
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  def create(url: URL, company: Company): Future[Website] =
    for {
      host                   <- url.getHost.liftTo[Future](MalformedHost(url.value))
      createdCompany         <- companyRepository.getOrCreate(company.siret, company)
      identified             <- repository.listIdentified(host)
      notAssociatedToCompany <- repository.listNotAssociatedToCompany(host)
      _ <-
        if (notAssociatedToCompany.nonEmpty || identified.flatMap(_.companyId).contains(createdCompany.id))
          Future.failed(CreateWebsiteError("Impossible de créer un site web s'il est déjà associé mais pas identifié"))
        else
          Future.unit
      website = Website(
        host = host,
        companyCountry = None,
        companyId = Some(createdCompany.id),
        identificationStatus = IdentificationStatus.Identified
      )
      createdWebsite <- repository.create(website)
    } yield createdWebsite

  def searchByHost(host: String): Future[Seq[Country]] =
    for {
      validHost <- URL(host).getHost.liftTo[Future](MalformedHost(host))
      websites  <- repository.searchValidWebsiteCountryAssociationByHost(validHost)
    } yield websites
      .flatMap(_.companyCountry)
      .map(Country.fromCode)

  def getWebsiteCompanyCount(
      maybeHost: Option[String],
      identificationStatus: Option[Seq[IdentificationStatus]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int],
      investigationStatusFilter: Option[Seq[InvestigationStatus]],
      start: Option[OffsetDateTime],
      end: Option[OffsetDateTime],
      hasAssociation: Option[Boolean],
      isOpen: Option[Boolean]
  ): Future[PaginatedResult[WebsiteCompanyReportCount]] =
    for {
      websites <- repository.listWebsitesCompaniesByReportCount(
        maybeHost,
        identificationStatus,
        maybeOffset,
        maybeLimit,
        investigationStatusFilter,
        start,
        end,
        hasAssociation,
        isOpen
      )
      _                 = logger.debug("Website company report fetched")
      websitesWithCount = websites.copy(entities = websites.entities.map(toApi))
    } yield websitesWithCount

  def updateWebsiteIdentificationStatus(
      websiteId: WebsiteId,
      newIdentificationStatus: IdentificationStatus,
      user: User
  ): Future[Website] = for {
    website    <- findWebsite(websiteId)
    identified <- repository.listIdentified(website.host)
    _ <-
      if (identified.length > 1)
        Future.failed(
          CreateWebsiteError("Le status ne peut pas être modifié si plus d'un siret est associé au site web")
        )
      else Future.unit
    _ = if (website.companyCountry.isEmpty && website.companyId.isEmpty) {
      throw WebsiteNotIdentified(website.host)
    }
    _ <-
      if (newIdentificationStatus == Identified) { validateAndCleanAssociation(website) }
      else Future.unit
    _ = logger.debug(s"Updating website kind to ${newIdentificationStatus}")
    updatedWebsite <- update(website.copy(identificationStatus = newIdentificationStatus))

    _ <-
      if (newIdentificationStatus == Identified) {
        logger.debug(s"New status is $newIdentificationStatus, updating previous reports if company is defined")
        for {
          maybeCompany <- website.companyId
            .map(companyId => companyRepository.get(companyId))
            .getOrElse(Future.successful(None))
          _ = logger.debug(s"Company Siret is ${maybeCompany.map(_.siret)}")
          _ <- maybeCompany
            .map(company => updatePreviousReportsAssociatedToWebsite(website.host, company, user.id))
            .getOrElse(Future.unit)
        } yield ()
      } else Future.unit
  } yield updatedWebsite

  private def validateAndCleanAssociation(website: Website) = {
    logger.debug(s"Validating that ${website.host} has not already been validated")
    for {
      _ <- repository
        .searchValidAssociationByHost(website.host)
        .ensure(WebsiteHostIsAlreadyIdentified(website.host))(_.isEmpty)
      _ = logger.debug(s"Removing other websites with the same host : ${website.host}")
      _ <- repository.removeOtherNonIdentifiedWebsitesWithSameHost(website)
    } yield website
  }

  def updateCompany(websiteId: WebsiteId, companyToAssign: CompanyCreation, user: User): Future[WebsiteAndCompany] =
    for {
      company <- {
        logger.debug(s"Updating website (id ${websiteId}) with company siret : ${companyToAssign.siret}")
        getOrCreateCompany(companyToAssign)
      }
      website    <- findWebsite(websiteId)
      identified <- repository.listIdentified(website.host)
      _ <-
        if (identified.length > 1 && identified.flatMap(_.companyId).contains(company.id)) {
          Future.failed(
            CreateWebsiteError("L'entreprise ne peut pas être modifiée si plus d'un siret est associé au site web")
          )
        } else Future.unit
      websiteToUpdate = website.copy(
        companyCountry = None,
        companyId = Some(company.id)
      )
      updatedWebsite <- updateIdentification(websiteToUpdate, user)
      _              <- updatePreviousReportsAssociatedToWebsite(website.host, company, user.id)
    } yield WebsiteAndCompany.toApi(updatedWebsite, Some(company))

  def updateCompanyCountry(websiteId: WebsiteId, companyCountry: String, user: User): Future[WebsiteAndCompany] = for {
    website <- {
      logger.debug(s"Updating website (id ${websiteId.value}) with company country : $companyCountry")
      findWebsite(websiteId)
    }
    identified <- repository.listIdentified(website.host)
    _ <-
      if (identified.length > 1)
        Future.failed(CreateWebsiteError("Le pays ne peut pas être modifié si plus d'un siret est associé au site web"))
      else Future.unit
    websiteToUpdate = website.copy(
      companyCountry = Some(companyCountry),
      companyId = None
    )
    updatedWebsite <- updateIdentification(websiteToUpdate, user)
  } yield WebsiteAndCompany.toApi(updatedWebsite, maybeCompany = None)

  private def updateIdentification(website: Website, user: User) = {
    logger.debug(s"Removing other websites with the same host : ${website.host}")
    for {
      _ <- repository
        .removeOtherNonIdentifiedWebsitesWithSameHost(website)
      _               = logger.debug(s"updating identification status when Admin is updating identification")
      websiteToUpdate = if (user.isAdmin) website.copy(identificationStatus = Identified) else website
      _               = logger.debug(s"Website to update : ${websiteToUpdate}")
      updatedWebsite <- update(websiteToUpdate)
      _ = logger.debug(s"Website company country successfully updated")
    } yield updatedWebsite
  }

  def delete(websiteId: WebsiteId): Future[Unit] =
    for {
      maybeWebsite <- repository.get(websiteId)
      website      <- maybeWebsite.liftTo[Future](WebsiteNotFound(websiteId))
      identified   <- repository.listIdentified(website.host)
      isWebsiteUnderInvestigation = website.investigationStatus != NotProcessed
      isWebsiteIdentified         = website.identificationStatus == Identified
      _ <-
        if (identified.length < 2 && (isWebsiteIdentified || isWebsiteUnderInvestigation)) {
          logger.debug(s"Cannot delete identified / under investigation website")
          Future.failed(CannotDeleteWebsite(website.host))
        } else {
          Future.unit
        }
      _ <- repository.delete(websiteId)
    } yield ()

  def updateInvestigation(investigationApi: WebsiteInvestigationApi): Future[Website] = for {
    maybeWebsite <- repository.get(investigationApi.id)
    website      <- maybeWebsite.liftTo[Future](WebsiteNotFound(investigationApi.id))
    _              = logger.debug("Update investigation")
    updatedWebsite = investigationApi.copyToDomain(website)
    website <- update(updatedWebsite)
  } yield website

  def listInvestigationStatus(): Seq[InvestigationStatus] = InvestigationStatus.values

  private[this] def getOrCreateCompany(companyCreate: CompanyCreation): Future[Company] = companyRepository
    .getOrCreate(
      companyCreate.siret,
      companyCreate.toCompany()
    )

  private[this] def findWebsite(websiteId: WebsiteId): Future[Website] = for {
    maybeWebsite <- {
      logger.debug(s"Searching for website with id : $websiteId")
      repository.get(websiteId)
    }
    website <- maybeWebsite.liftTo[Future](WebsiteNotFound(websiteId))
    _ = logger.debug(s"Found website")
  } yield website

  private def update(website: Website) =
    repository.update(website.id, website.copy(lastUpdated = OffsetDateTime.now()))

  def fetchUnregisteredHost(
      host: Option[String],
      start: Option[String],
      end: Option[String]
  ): Future[List[WebsiteHostCount]] =
    repository
      .getUnkonwnReportCountByHost(host, DateUtils.parseDate(start), DateUtils.parseDate(end))
      .map(_.map { case (host, count) => WebsiteHostCount(host, count) })

  private def updatePreviousReportsAssociatedToWebsite(
      websiteHost: String,
      company: Company,
      userId: UUID
  ): Future[Unit] = {
    val reportCompany = ReportCompany(
      name = company.name,
      address = company.address,
      siret = company.siret,
      activityCode = company.activityCode,
      isHeadOffice = company.isHeadOffice,
      isOpen = company.isOpen,
      isPublic = company.isPublic,
      brand = company.brand,
      commercialName = company.commercialName,
      establishmentCommercialName = company.establishmentCommercialName
    )
    for {
      reports <- reportRepository.getForWebsiteWithoutCompany(websiteHost)
      _ <- reports.traverse(reportId =>
        reportOrchestrator.updateReportCompanyForWebsite(reportId, reportCompany, userId)
      )
    } yield ()
  }

}
