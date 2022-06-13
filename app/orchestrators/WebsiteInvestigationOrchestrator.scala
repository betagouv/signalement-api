package orchestrators

import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import models.PaginatedResult
import models.investigation.DepartmentDivision
import models.investigation.DepartmentDivisionOptionValue
import models.investigation.InvestigationStatus
import models.investigation.Practice
import models.investigation.WebsiteInvestigation
import models.investigation.WebsiteInvestigationApi
import models.investigation.WebsiteInvestigationCompanyReportCount
import models.investigation.WebsiteInvestigationCompanyReportCount.toApi
import models.website.WebsiteKind
import play.api.Logger
import repositories.websiteinvestigation.WebsiteInvestigationRepositoryInterface
import controllers.error.AppError._
import repositories.website.WebsiteRepositoryInterface

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class WebsiteInvestigationOrchestrator(
    val repository: WebsiteInvestigationRepositoryInterface,
    val websiteRepository: WebsiteRepositoryInterface
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  def list(
      maybeHost: Option[String],
      kinds: Option[Seq[WebsiteKind]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[WebsiteInvestigationCompanyReportCount]] =
    for {
      websites <- repository.listWebsiteInvestigation(maybeHost, kinds, maybeOffset, maybeLimit)
      _ = logger.debug("Website investigation fetched")
      websitesWithCount = websites.copy(entities = websites.entities.map(toApi))
    } yield websitesWithCount

  def createOrUpdate(investigationApi: WebsiteInvestigationApi): Future[WebsiteInvestigation] = for {
    maybeWebsite <- websiteRepository.get(investigationApi.websiteId)
    _ <- maybeWebsite.liftTo[Future](WebsiteNotFound(investigationApi.websiteId))
    _ = logger.debug("Create or Update investigation")
    maybeInvestigation <- investigationApi.id.map(repository.get).flatSequence
    _ = maybeInvestigation.fold(logger.debug("Investigation not found, creating it"))(_ =>
      logger.debug("Found investigation, updating it")
    )
    investigation = investigationApi.createOrCopyToDomain(maybeInvestigation)
    websiteInvestigationResult <- repository.createOrUpdate(investigation)
  } yield websiteInvestigationResult

  def listDepartmentDivision(): Seq[DepartmentDivisionOptionValue] =
    DepartmentDivision.values.map(d => DepartmentDivisionOptionValue(d.entryName, d.name))

  def listInvestigationStatus(): Seq[InvestigationStatus] = InvestigationStatus.values

  def listPractice(): Seq[Practice] = Practice.values

}
